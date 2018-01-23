package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class Order {

    private final long id;
    private final long accountId;
    private final long assetId;
    private final long priceNQT;
    private final int creationHeight;
    private final short transactionIndex;
    private final int transactionHeight;
    private long quantityQNT;
    private Order(Transaction transaction, Mortgaged.ColoredCoinsOrderPlacement attachment) {
        this.id = transaction.getTransactionId();
        this.accountId = transaction.getSenderId();
        this.assetId = attachment.getAssetId();
        this.quantityQNT = attachment.getQuantityQNT();
        this.priceNQT = attachment.getPriceNQT();
        this.creationHeight = EcBlockchainImpl.getInstance().getHeight();
        this.transactionIndex = transaction.getTransactionIndex();
        this.transactionHeight = transaction.getTransactionHeight();
    }

    private Order(ResultSet rs) throws SQLException {
        this.id = rs.getLong("Id");
        this.accountId = rs.getLong("account_id");
        this.assetId = rs.getLong("asset_id");
        this.priceNQT = rs.getLong("price");
        this.quantityQNT = rs.getLong("quantity");
        this.creationHeight = rs.getInt("creation_height");
        this.transactionIndex = rs.getShort("transaction_index");
        this.transactionHeight = rs.getInt("transaction_height");
    }

    private static void matchOrders(long assetId) {

        Order.Ask askOrder;
        Order.Bid bidOrder;

        while ((askOrder = Ask.getNextOrder(assetId)) != null
                && (bidOrder = Bid.getNextOrder(assetId)) != null) {

            if (askOrder.getPriceNQT() > bidOrder.getPriceNQT()) {
                break;
            }

            Trade trade = Trade.addTrade(assetId, askOrder, bidOrder);

            askOrder.updateQuantityQNT(Math.subtractExact(askOrder.getQuantityQNT(), trade.getQuantityQNT()));
            Account askAccount = Account.getAccount(askOrder.getAccountId());
            askAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.ASSET_TRADE, askOrder.getId(),
                    Math.multiplyExact(trade.getQuantityQNT(), trade.getPriceNQT()));
            askAccount.addToAssetBalanceQNT(LedgerEvent.ASSET_TRADE, askOrder.getId(), assetId, -trade.getQuantityQNT());

            bidOrder.updateQuantityQNT(Math.subtractExact(bidOrder.getQuantityQNT(), trade.getQuantityQNT()));
            Account bidAccount = Account.getAccount(bidOrder.getAccountId());
            bidAccount.addToAssetAndUnconfirmedAssetBalanceQNT(LedgerEvent.ASSET_TRADE, bidOrder.getId(),
                    assetId, trade.getQuantityQNT());
            bidAccount.addToBalanceNQT(LedgerEvent.ASSET_TRADE, bidOrder.getId(),
                    -Math.multiplyExact(trade.getQuantityQNT(), trade.getPriceNQT()));
            bidAccount.addToUnconfirmedBalanceNQT(LedgerEvent.ASSET_TRADE, bidOrder.getId(),
                    Math.multiplyExact(trade.getQuantityQNT(), (bidOrder.getPriceNQT() - trade.getPriceNQT())));
        }

    }

    public static void start() {
        Ask.init();
        Bid.init();
    }

    private void save(Connection con, String table) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO " + table + " (id, account_id, asset_id, "
                + "price, quantity, creation_height, transaction_index, transaction_height, height, latest) KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.priceNQT);
            pstmt.setLong(++i, this.quantityQNT);
            pstmt.setInt(++i, this.creationHeight);
            pstmt.setShort(++i, this.transactionIndex);
            pstmt.setInt(++i, this.transactionHeight);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public final long getId() {
        return id;
    }

    public final long getAccountId() {
        return accountId;
    }

    public final long getAssetId() {
        return assetId;
    }

    public final long getPriceNQT() {
        return priceNQT;
    }

    public final long getQuantityQNT() {
        return quantityQNT;
    }

    private void setQuantityQNT(long quantityQNT) {
        this.quantityQNT = quantityQNT;
    }

    public final int getHeight() {
        return creationHeight;
    }

    public final int getTransactionIndex() {
        return transactionIndex;
    }

    public final int getTransactionHeight() {
        return transactionHeight;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " Id: " + Long.toUnsignedString(id) + " account: " + Long.toUnsignedString(accountId)
                + " asset: " + Long.toUnsignedString(assetId) + " price: " + priceNQT + " quantity: " + quantityQNT
                + " height: " + creationHeight + " transactionIndex: " + transactionIndex + " transactionHeight: " + transactionHeight;
    }

    public static final class Ask extends Order {

        private static final H2KeyLongKeyFactory<Ask> askOrderDbKeyFactory = new H2KeyLongKeyFactory<Ask>("Id") {

            @Override
            public H2Key newKey(Ask ask) {
                return ask.h2Key;
            }

        };

        private static final VersionedEntityH2Table<Ask> askOrderTable = new VersionedEntityH2Table<Ask>("ask_order", askOrderDbKeyFactory) {
            @Override
            protected Ask load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
                return new Ask(rs, h2Key);
            }

            @Override
            protected void save(Connection con, Ask ask) throws SQLException {
                ask.save(con, table);
            }

            @Override
            protected String defaultSort() {
                return " ORDER BY creation_height DESC ";
            }

        };
        private final H2Key h2Key;

        private Ask(Transaction transaction, Mortgaged.ColoredCoinsAskOrderPlacement attachment) {
            super(transaction, attachment);
            this.h2Key = askOrderDbKeyFactory.newKey(super.id);
        }

        private Ask(ResultSet rs, H2Key h2Key) throws SQLException {
            super(rs);
            this.h2Key = h2Key;
        }

        public static int getCount() {
            return askOrderTable.getCount();
        }

        public static Ask getAskOrder(long orderId) {
            return askOrderTable.get(askOrderDbKeyFactory.newKey(orderId));
        }

        public static H2Iterator<Ask> getAll(int from, int to) {
            return askOrderTable.getAll(from, to);
        }

        public static H2Iterator<Ask> getAskOrdersByAccount(long accountId, int from, int to) {
            return askOrderTable.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
        }

        public static H2Iterator<Ask> getAskOrdersByAsset(long assetId, int from, int to) {
            return askOrderTable.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to);
        }

        public static H2Iterator<Ask> getAskOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
            H2Clause h2Clause = new H2ClauseLongClause("account_id", accountId).and(new H2ClauseLongClause("asset_id", assetId));
            return askOrderTable.getManyBy(h2Clause, from, to);
        }

        public static H2Iterator<Ask> getSortedOrders(long assetId, int from, int to) {
            return askOrderTable.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to,
                    " ORDER BY price ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
        }

        private static Ask getNextOrder(long assetId) {
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM ask_order WHERE asset_id = ? "
                         + "AND latest = TRUE ORDER BY price ASC, creation_height ASC, transaction_height ASC, transaction_index ASC LIMIT 1")) {
                pstmt.setLong(1, assetId);
                try (H2Iterator<Ask> askOrders = askOrderTable.getManyBy(con, pstmt, true)) {
                    return askOrders.hasNext() ? askOrders.next() : null;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        static void addOrder(Transaction transaction, Mortgaged.ColoredCoinsAskOrderPlacement attachment) {
            Ask order = new Ask(transaction, attachment);
            askOrderTable.insert(order);
            matchOrders(attachment.getAssetId());
        }

        static void removeOrder(long orderId) {
            askOrderTable.delete(getAskOrder(orderId));
        }

        public static void init() {
        }

        private void save(Connection con, String table) throws SQLException {
            super.save(con, table);
        }

        private void updateQuantityQNT(long quantityQNT) {
            super.setQuantityQNT(quantityQNT);
            if (quantityQNT > 0) {
                askOrderTable.insert(this);
            } else if (quantityQNT == 0) {
                askOrderTable.delete(this);
            } else {
                throw new IllegalArgumentException("Negative quantity: " + quantityQNT
                        + " for order: " + Long.toUnsignedString(getId()));
            }
        }

        /*
        @Override
        public int compareTo(Ask o) {
            if (this.getPriceNQT() < o.getPriceNQT()) {
                return -1;
            } else if (this.getPriceNQT() > o.getPriceNQT()) {
                return 1;
            } else {
                return super.compareTo(o);
            }
        }
        */

    }

    public static final class Bid extends Order {

        private static final H2KeyLongKeyFactory<Bid> bidOrderDbKeyFactory = new H2KeyLongKeyFactory<Bid>("Id") {

            @Override
            public H2Key newKey(Bid bid) {
                return bid.h2Key;
            }

        };

        private static final VersionedEntityH2Table<Bid> bidOrderTable = new VersionedEntityH2Table<Bid>("bid_order", bidOrderDbKeyFactory) {

            @Override
            protected Bid load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
                return new Bid(rs, h2Key);
            }

            @Override
            protected void save(Connection con, Bid bid) throws SQLException {
                bid.save(con, table);
            }

            @Override
            protected String defaultSort() {
                return " ORDER BY creation_height DESC ";
            }

        };
        private final H2Key h2Key;

        private Bid(Transaction transaction, Mortgaged.ColoredCoinsBidOrderPlacement attachment) {
            super(transaction, attachment);
            this.h2Key = bidOrderDbKeyFactory.newKey(super.id);
        }

        private Bid(ResultSet rs, H2Key h2Key) throws SQLException {
            super(rs);
            this.h2Key = h2Key;
        }

        public static int getCount() {
            return bidOrderTable.getCount();
        }

        public static Bid getBidOrder(long orderId) {
            return bidOrderTable.get(bidOrderDbKeyFactory.newKey(orderId));
        }

        public static H2Iterator<Bid> getAll(int from, int to) {
            return bidOrderTable.getAll(from, to);
        }

        public static H2Iterator<Bid> getBidOrdersByAccount(long accountId, int from, int to) {
            return bidOrderTable.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
        }

        public static H2Iterator<Bid> getBidOrdersByAsset(long assetId, int from, int to) {
            return bidOrderTable.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to);
        }

        public static H2Iterator<Bid> getBidOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
            H2Clause h2Clause = new H2ClauseLongClause("account_id", accountId).and(new H2ClauseLongClause("asset_id", assetId));
            return bidOrderTable.getManyBy(h2Clause, from, to);
        }

        public static H2Iterator<Bid> getSortedOrders(long assetId, int from, int to) {
            return bidOrderTable.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to,
                    " ORDER BY price DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
        }

        private static Bid getNextOrder(long assetId) {
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM bid_order WHERE asset_id = ? "
                         + "AND latest = TRUE ORDER BY price DESC, creation_height ASC, transaction_height ASC, transaction_index ASC LIMIT 1")) {
                pstmt.setLong(1, assetId);
                try (H2Iterator<Bid> bidOrders = bidOrderTable.getManyBy(con, pstmt, true)) {
                    return bidOrders.hasNext() ? bidOrders.next() : null;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        static void addOrder(Transaction transaction, Mortgaged.ColoredCoinsBidOrderPlacement attachment) {
            Bid order = new Bid(transaction, attachment);
            bidOrderTable.insert(order);
            matchOrders(attachment.getAssetId());
        }

        static void removeOrder(long orderId) {
            bidOrderTable.delete(getBidOrder(orderId));
        }

        public static void init() {
        }

        private void save(Connection con, String table) throws SQLException {
            super.save(con, table);
        }

        private void updateQuantityQNT(long quantityQNT) {
            super.setQuantityQNT(quantityQNT);
            if (quantityQNT > 0) {
                bidOrderTable.insert(this);
            } else if (quantityQNT == 0) {
                bidOrderTable.delete(this);
            } else {
                throw new IllegalArgumentException("Negative quantity: " + quantityQNT
                        + " for order: " + Long.toUnsignedString(getId()));
            }
        }

        /*
        @Override
        public int compareTo(Bid o) {
            if (this.getPriceNQT() > o.getPriceNQT()) {
                return -1;
            } else if (this.getPriceNQT() < o.getPriceNQT()) {
                return 1;
            } else {
                return super.compareTo(o);
            }
        }
        */
    }
}
