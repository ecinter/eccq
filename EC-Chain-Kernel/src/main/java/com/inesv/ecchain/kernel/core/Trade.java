package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Listener;
import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class Trade {

    private static final ListenerManager<Trade, TradeEvent> LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLinkKeyFactory<Trade> TRADE_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<Trade>("ask_order_id", "bid_order_id") {

        @Override
        public H2Key newKey(Trade trade) {
            return trade.h2Key;
        }

    };
    private static final EntityH2Table<Trade> TRADE_TABLE = new EntityH2Table<Trade>("trade", TRADE_DB_KEY_FACTORY) {

        @Override
        protected Trade load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Trade(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Trade trade) throws SQLException {
            trade.saveTrade(con);
        }

    };
    private final int timestamp;
    private final long assetId;
    private final long blockId;
    private final int height;
    private final long askOrderId;
    private final long bidOrderId;
    private final int askOrderHeight;
    private final int bidOrderHeight;
    private final long sellerId;
    private final long buyerId;
    private final H2Key h2Key;
    private final long quantityQNT;
    private final long priceNQT;
    private final boolean isBuy;

    private Trade(long assetId, Order.Ask askOrder, Order.Bid bidOrder) {
        EcBlock ecBlock = EcBlockchainImpl.getInstance().getLastECBlock();
        this.blockId = ecBlock.getECId();
        this.height = ecBlock.getHeight();
        this.assetId = assetId;
        this.timestamp = ecBlock.getTimestamp();
        this.askOrderId = askOrder.getId();
        this.bidOrderId = bidOrder.getId();
        this.askOrderHeight = askOrder.getHeight();
        this.bidOrderHeight = bidOrder.getHeight();
        this.sellerId = askOrder.getAccountId();
        this.buyerId = bidOrder.getAccountId();
        this.h2Key = TRADE_DB_KEY_FACTORY.newKey(this.askOrderId, this.bidOrderId);
        this.quantityQNT = Math.min(askOrder.getQuantityQNT(), bidOrder.getQuantityQNT());
        if (askOrderHeight < bidOrderHeight) {
            this.isBuy = true;
        } else if (askOrderHeight == bidOrderHeight) {
            if (this.height <= Constants.EC_PHASING_BLOCK) {
                this.isBuy = askOrderId < bidOrderId;
            } else {
                this.isBuy = askOrder.getTransactionHeight() < bidOrder.getTransactionHeight() ||
                        (askOrder.getTransactionHeight() == bidOrder.getTransactionHeight()
                                && askOrder.getTransactionIndex() < bidOrder.getTransactionIndex());
            }
        } else {
            this.isBuy = false;
        }
        this.priceNQT = isBuy ? askOrder.getPriceNQT() : bidOrder.getPriceNQT();
    }

    private Trade(ResultSet rs, H2Key h2Key) throws SQLException {
        this.assetId = rs.getLong("asset_id");
        this.blockId = rs.getLong("block_id");
        this.askOrderId = rs.getLong("ask_order_id");
        this.bidOrderId = rs.getLong("bid_order_id");
        this.askOrderHeight = rs.getInt("ask_order_height");
        this.bidOrderHeight = rs.getInt("bid_order_height");
        this.sellerId = rs.getLong("seller_id");
        this.buyerId = rs.getLong("buyer_id");
        this.h2Key = h2Key;
        this.quantityQNT = rs.getLong("quantity");
        this.priceNQT = rs.getLong("price");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
        this.isBuy = rs.getBoolean("is_buy");
    }

    public static H2Iterator<Trade> getAllTrades(int from, int to) {
        return TRADE_TABLE.getAll(from, to);
    }

    public static int getCount() {
        return TRADE_TABLE.getCount();
    }

    public static boolean addTradeListener(Listener<Trade> listener, TradeEvent eventType) {
        return LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static Trade getTrade(long askOrderId, long bidOrderId) {
        return TRADE_TABLE.get(TRADE_DB_KEY_FACTORY.newKey(askOrderId, bidOrderId));
    }

    public static H2Iterator<Trade> getAssetTrades(long assetId, int from, int to) {
        return TRADE_TABLE.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to);
    }

    public static List<Trade> getLastTrades(long[] assetIds) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM trade WHERE asset_id = ? ORDER BY asset_id, height DESC LIMIT 1")) {
            List<Trade> result = new ArrayList<>();
            for (long assetId : assetIds) {
                pstmt.setLong(1, assetId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        result.add(new Trade(rs, null));
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<Trade> getAccountTrades(long accountId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM trade WHERE seller_id = ?"
                    + " UNION ALL SELECT * FROM trade WHERE buyer_id = ? AND seller_id <> ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return TRADE_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<Trade> getAccountAssetTrades(long accountId, long assetId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM trade WHERE seller_id = ? AND asset_id = ?"
                    + " UNION ALL SELECT * FROM trade WHERE buyer_id = ? AND seller_id <> ? AND asset_id = ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, assetId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, assetId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return TRADE_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<Trade> getAskOrderTrades(long askOrderId, int from, int to) {
        return TRADE_TABLE.getManyBy(new H2ClauseLongClause("ask_order_id", askOrderId), from, to);
    }

    public static H2Iterator<Trade> getBidOrderTrades(long bidOrderId, int from, int to) {
        return TRADE_TABLE.getManyBy(new H2ClauseLongClause("bid_order_id", bidOrderId), from, to);
    }

    public static int getTradeCount(long assetId) {
        return TRADE_TABLE.getCount(new H2ClauseLongClause("asset_id", assetId));
    }

    static Trade addTrade(long assetId, Order.Ask askOrder, Order.Bid bidOrder) {
        Trade trade = new Trade(assetId, askOrder, bidOrder);
        TRADE_TABLE.insert(trade);
        LISTENER_MANAGER.notify(trade, TradeEvent.TRADE);
        return trade;
    }

    public static void start() {
    }

    private void saveTrade(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO trade (asset_id, block_id, "
                + "ask_order_id, bid_order_id, ask_order_height, bid_order_height, seller_id, buyer_id, quantity, price, is_buy, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.blockId);
            pstmt.setLong(++i, this.askOrderId);
            pstmt.setLong(++i, this.bidOrderId);
            pstmt.setInt(++i, this.askOrderHeight);
            pstmt.setInt(++i, this.bidOrderHeight);
            pstmt.setLong(++i, this.sellerId);
            pstmt.setLong(++i, this.buyerId);
            pstmt.setLong(++i, this.quantityQNT);
            pstmt.setLong(++i, this.priceNQT);
            pstmt.setBoolean(++i, this.isBuy);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, this.height);
            pstmt.executeUpdate();
        }
    }

    public long getBlockId() {
        return blockId;
    }

    public long getOrderId() {
        return askOrderId;
    }

    public long getBidOrderId() {
        return bidOrderId;
    }

    public int getOrderHeight() {
        return askOrderHeight;
    }

    public int getBidOrderHeight() {
        return bidOrderHeight;
    }

    public long getSellerId() {
        return sellerId;
    }

    public long getBuyerId() {
        return buyerId;
    }

    public long getQuantityQNT() {
        return quantityQNT;
    }

    public long getPriceNQT() {
        return priceNQT;
    }

    public long getAssetId() {
        return assetId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

    public boolean isBuy() {
        return isBuy;
    }

    @Override
    public String toString() {
        return "Trade asset: " + Long.toUnsignedString(assetId) + " ask: " + Long.toUnsignedString(askOrderId)
                + " bid: " + Long.toUnsignedString(bidOrderId) + " price: " + priceNQT + " quantity: " + quantityQNT + " height: " + height;
    }

}
