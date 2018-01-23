package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.Listener;
import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class Conversion {

    private static final ListenerManager<Conversion, ConversionEvent> CONVERSION_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLinkKeyFactory<Conversion> EXCHANGE_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<Conversion>("transaction_id", "offer_id") {

        @Override
        public H2Key newKey(Conversion conversion) {
            return conversion.h2Key;
        }

    };
    private static final EntityH2Table<Conversion> EXCHANGE_TABLE = new EntityH2Table<Conversion>("exchange", EXCHANGE_DB_KEY_FACTORY) {

        @Override
        protected Conversion load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Conversion(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Conversion conversion) throws SQLException {
            conversion.save(con);
        }

    };
    private final long transactionId;
    private final int timestamp;
    private final long currencyId;
    private final long blockId;
    private final int height;
    private final long offerId;
    private final long sellerId;
    private final long buyerId;
    private final H2Key h2Key;
    private final long units;
    private final long rate;

    private Conversion(long transactionId, long currencyId, CoinExchangeOffer offer, long sellerId, long buyerId, long units) {
        EcBlock ecBlock = EcBlockchainImpl.getInstance().getLastECBlock();
        this.transactionId = transactionId;
        this.blockId = ecBlock.getECId();
        this.height = ecBlock.getHeight();
        this.currencyId = currencyId;
        this.timestamp = ecBlock.getTimestamp();
        this.offerId = offer.getId();
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.h2Key = EXCHANGE_DB_KEY_FACTORY.newKey(this.transactionId, this.offerId);
        this.units = units;
        this.rate = offer.getRateNQT();
    }

    private Conversion(ResultSet rs, H2Key h2Key) throws SQLException {
        this.transactionId = rs.getLong("transaction_id");
        this.currencyId = rs.getLong("currency_id");
        this.blockId = rs.getLong("block_id");
        this.offerId = rs.getLong("offer_id");
        this.sellerId = rs.getLong("seller_id");
        this.buyerId = rs.getLong("buyer_id");
        this.h2Key = h2Key;
        this.units = rs.getLong("units");
        this.rate = rs.getLong("rate");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
    }

    public static H2Iterator<Conversion> getAllExchanges(int from, int to) {
        return EXCHANGE_TABLE.getAll(from, to);
    }

    public static int getCount() {
        return EXCHANGE_TABLE.getCount();
    }

    public static boolean addConversionListener(Listener<Conversion> listener, ConversionEvent eventType) {
        return CONVERSION_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static H2Iterator<Conversion> getCoinExchanges(long currencyId, int from, int to) {
        return EXCHANGE_TABLE.getManyBy(new H2ClauseLongClause("currency_id", currencyId), from, to);
    }

    public static List<Conversion> getLastConvert(long[] currencyIds) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM exchange WHERE currency_id = ? ORDER BY height DESC, db_id DESC LIMIT 1")) {
            List<Conversion> result = new ArrayList<>();
            for (long currencyId : currencyIds) {
                pstmt.setLong(1, currencyId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        result.add(new Conversion(rs, null));
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<Conversion> getAccountConvert(long accountId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM exchange WHERE seller_id = ?"
                    + " UNION ALL SELECT * FROM exchange WHERE buyer_id = ? AND seller_id <> ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return EXCHANGE_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<Conversion> getAccountCurrencyConvert(long accountId, long currencyId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM exchange WHERE seller_id = ? AND currency_id = ?"
                    + " UNION ALL SELECT * FROM exchange WHERE buyer_id = ? AND seller_id <> ? AND currency_id = ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, currencyId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, currencyId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return EXCHANGE_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<Conversion> getConvert(long transactionId) {
        return EXCHANGE_TABLE.getManyBy(new H2ClauseLongClause("transaction_id", transactionId), 0, -1);
    }

    public static H2Iterator<Conversion> getOfferConvert(long offerId, int from, int to) {
        return EXCHANGE_TABLE.getManyBy(new H2ClauseLongClause("offer_id", offerId), from, to);
    }

    public static int getConvertCount(long currencyId) {
        return EXCHANGE_TABLE.getCount(new H2ClauseLongClause("currency_id", currencyId));
    }

    static Conversion addConvert(Transaction transaction, long currencyId, CoinExchangeOffer offer, long sellerId, long buyerId, long units) {
        Conversion conversion = new Conversion(transaction.getTransactionId(), currencyId, offer, sellerId, buyerId, units);
        EXCHANGE_TABLE.insert(conversion);
        CONVERSION_EVENT_LISTENER_MANAGER.notify(conversion, ConversionEvent.EXCHANGE);
        return conversion;
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO exchange (transaction_id, currency_id, block_id, "
                + "offer_id, seller_id, buyer_id, units, rate, timestamp, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.transactionId);
            pstmt.setLong(++i, this.currencyId);
            pstmt.setLong(++i, this.blockId);
            pstmt.setLong(++i, this.offerId);
            pstmt.setLong(++i, this.sellerId);
            pstmt.setLong(++i, this.buyerId);
            pstmt.setLong(++i, this.units);
            pstmt.setLong(++i, this.rate);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, this.height);
            pstmt.executeUpdate();
        }
    }

    public long getTransactionId() {
        return transactionId;
    }

    public long getBlockId() {
        return blockId;
    }

    public long getOfferId() {
        return offerId;
    }

    public long getSellerId() {
        return sellerId;
    }

    public long getBuyerId() {
        return buyerId;
    }

    public long getUnits() {
        return units;
    }

    public long getRate() {
        return rate;
    }

    public long getCurrencyId() {
        return currencyId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String toString() {
        return "Conversion currency: " + Long.toUnsignedString(currencyId) + " offer: " + Long.toUnsignedString(offerId)
                + " rate: " + rate + " units: " + units + " height: " + height + " transaction: " + Long.toUnsignedString(transactionId);
    }

}
