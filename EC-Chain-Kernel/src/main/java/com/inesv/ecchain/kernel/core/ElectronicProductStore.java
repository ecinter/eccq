package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.common.util.Search;
import com.inesv.ecchain.kernel.H2.*;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class ElectronicProductStore {

    private static final ListenerManager<Goods, ElectronicProductStoreEvent> GOODS_LISTENER_MANAGER = new ListenerManager<>();
    private static final ListenerManager<Purchase, ElectronicProductStoreEvent> PURCHASE_LISTENER_MANAGER = new ListenerManager<>();
    @PostConstruct
    public static void initPostConstruct()
     {
        EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
            if (block.getHeight() <= Constants.EC_DIGITAL_GOODS_STORE_BLOCK) {
                return;
            }
            List<Purchase> expiredPurchases = new ArrayList<>();
            try (H2Iterator<Purchase> iterator = Purchase.getExpiredPendingPurchases(block)) {
                while (iterator.hasNext()) {
                    expiredPurchases.add(iterator.next());
                }
            }
            for (Purchase purchase : expiredPurchases) {
                Account buyer = Account.getAccount(purchase.getBuyerId());
                buyer.addToUnconfirmedBalanceNQT(LedgerEvent.DIGITAL_GOODS_PURCHASE_EXPIRED, purchase.getId(),
                        Math.multiplyExact((long) purchase.getQuantity(), purchase.getPriceNQT()));
                Goods.getGoods(purchase.getGoodsId()).changeQuantity(purchase.getQuantity());
                purchase.setPending(false);
            }
        }, EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);
    }

    public static void start() {
        Tag.init();
        Goods.init();
        Purchase.init();
    }

    static void listGoods(Transaction transaction, Mortgaged.DigitalGoodsListing attachment) {
        Goods goods = new Goods(transaction, attachment);
        Tag.add(goods);
        Goods.goodsTable.insert(goods);
        GOODS_LISTENER_MANAGER.notify(goods, ElectronicProductStoreEvent.GOODS_LISTED);
    }

    static void delistGoods(long goodsId) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(goodsId));
        if (!goods.isDelisted()) {
            goods.setDelisted(true);
            GOODS_LISTENER_MANAGER.notify(goods, ElectronicProductStoreEvent.GOODS_DELISTED);
        } else {
            throw new IllegalStateException("Goods already delisted");
        }
    }

    static void changePrice(long goodsId, long priceNQT) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(goodsId));
        if (!goods.isDelisted()) {
            goods.changePrice(priceNQT);
            GOODS_LISTENER_MANAGER.notify(goods, ElectronicProductStoreEvent.GOODS_PRICE_CHANGE);
        } else {
            throw new IllegalStateException("Can't change price of delisted goods");
        }
    }

    static void changeQuantity(long goodsId, int deltaQuantity) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(goodsId));
        if (!goods.isDelisted()) {
            goods.changeQuantity(deltaQuantity);
            GOODS_LISTENER_MANAGER.notify(goods, ElectronicProductStoreEvent.GOODS_QUANTITY_CHANGE);
        } else {
            throw new IllegalStateException("Can't change quantity of delisted goods");
        }
    }

    static void purchase(Transaction transaction, Mortgaged.DigitalGoodsPurchase attachment) {
        Goods goods = Goods.goodsTable.get(Goods.goodsDbKeyFactory.newKey(attachment.getGoodsId()));
        if (!goods.isDelisted()
                && attachment.getQuantity() <= goods.getQuantity()
                && attachment.getPriceNQT() == goods.getPriceNQT()) {
            goods.changeQuantity(-attachment.getQuantity());
            Purchase purchase = new Purchase(transaction, attachment, goods.getSellerId());
            Purchase.purchaseTable.insert(purchase);
            PURCHASE_LISTENER_MANAGER.notify(purchase, ElectronicProductStoreEvent.PURCHASE);
        } else {
            Account buyer = Account.getAccount(transaction.getSenderId());
            buyer.addToUnconfirmedBalanceNQT(LedgerEvent.DIGITAL_GOODS_DELISTED, transaction.getTransactionId(),
                    Math.multiplyExact((long) attachment.getQuantity(), attachment.getPriceNQT()));
            // restoring the unconfirmed balance if purchase not successful, however buyer still lost the transaction fees
        }
    }

    static void deliver(Transaction transaction, Mortgaged.DigitalGoodsDelivery attachment) {
        Purchase purchase = Purchase.getPendingPurchase(attachment.getPurchaseId());
        purchase.setPending(false);
        long totalWithoutDiscount = Math.multiplyExact((long) purchase.getQuantity(), purchase.getPriceNQT());
        Account buyer = Account.getAccount(purchase.getBuyerId());
        long transactionId = transaction.getTransactionId();
        buyer.addToBalanceNQT(LedgerEvent.DIGITAL_GOODS_DELIVERY, transactionId,
                Math.subtractExact(attachment.getDiscountNQT(), totalWithoutDiscount));
        buyer.addToUnconfirmedBalanceNQT(LedgerEvent.DIGITAL_GOODS_DELIVERY, transactionId, attachment.getDiscountNQT());
        Account seller = Account.getAccount(transaction.getSenderId());
        seller.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.DIGITAL_GOODS_DELIVERY, transactionId,
                Math.subtractExact(totalWithoutDiscount, attachment.getDiscountNQT()));
        purchase.setEncryptedGoods(attachment.getGoods(), attachment.goodsIsText());
        purchase.setDiscountNQT(attachment.getDiscountNQT());
        PURCHASE_LISTENER_MANAGER.notify(purchase, ElectronicProductStoreEvent.DELIVERY);
    }

    static void refund(LedgerEvent event, long eventId, long sellerId, long purchaseId, long refundNQT,
                       Enclosure.EncryptedMessage encryptedMessage) {
        Purchase purchase = Purchase.purchaseTable.get(Purchase.purchaseDbKeyFactory.newKey(purchaseId));
        Account seller = Account.getAccount(sellerId);
        seller.addToBalanceNQT(event, eventId, -refundNQT);
        Account buyer = Account.getAccount(purchase.getBuyerId());
        buyer.addToBalanceAndUnconfirmedBalanceNQT(event, eventId, refundNQT);
        if (encryptedMessage != null) {
            purchase.setRefundNote(encryptedMessage.getEncryptedData());
        }
        purchase.setRefundNQT(refundNQT);
        PURCHASE_LISTENER_MANAGER.notify(purchase, ElectronicProductStoreEvent.REFUND);
    }

    static void feedback(long purchaseId, Enclosure.EncryptedMessage encryptedMessage, Message message) {
        Purchase purchase = Purchase.purchaseTable.get(Purchase.purchaseDbKeyFactory.newKey(purchaseId));
        if (encryptedMessage != null) {
            purchase.addFeedbackNote(encryptedMessage.getEncryptedData());
        }
        if (message != null) {
            purchase.addPublicFeedback(Convert.toString(message.getMessage()));
        }
        PURCHASE_LISTENER_MANAGER.notify(purchase, ElectronicProductStoreEvent.FEEDBACK);
    }

    private static EncryptedData loadEncryptedData(ResultSet rs, String dataColumn, String nonceColumn) throws SQLException {
        byte[] data = rs.getBytes(dataColumn);
        if (data == null) {
            return null;
        }
        return new EncryptedData(data, rs.getBytes(nonceColumn));
    }

    private static int setEncryptedData(PreparedStatement pstmt, EncryptedData encryptedData, int i) throws SQLException {
        if (encryptedData == null) {
            pstmt.setNull(i++, Types.VARBINARY);
            pstmt.setNull(i++, Types.VARBINARY);
        } else {
            pstmt.setBytes(i++, encryptedData.getData());
            pstmt.setBytes(i++, encryptedData.getNonce());
        }
        return i;
    }

    public static final class Tag {

        private static final H2KeyStringKeyFactory<Tag> tagDbKeyFactory = new H2KeyStringKeyFactory<Tag>("tag") {
            @Override
            public H2Key newKey(Tag tag) {
                return tag.h2Key;
            }
        };

        private static final VersionedEntityH2Table<Tag> tagTable = new VersionedEntityH2Table<Tag>("tag", tagDbKeyFactory) {

            @Override
            protected Tag load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
                return new Tag(rs, h2Key);
            }

            @Override
            protected void save(Connection con, Tag tag) throws SQLException {
                tag.save(con);
            }

            @Override
            public String defaultSort() {
                return " ORDER BY in_stock_count DESC, total_count DESC, tag ASC ";
            }

        };
        private static final H2Clause inStockOnlyClause = new H2ClauseIntClause("in_stock_count", H2ClauseOp.GT, 0);
        private final String tag;
        private final H2Key h2Key;
        private int inStockCount;
        private int totalCount;

        private Tag(String tag) {
            this.tag = tag;
            this.h2Key = tagDbKeyFactory.newKey(this.tag);
        }

        private Tag(ResultSet rs, H2Key h2Key) throws SQLException {
            this.tag = rs.getString("tag");
            this.h2Key = h2Key;
            this.inStockCount = rs.getInt("in_stock_count");
            this.totalCount = rs.getInt("total_count");
        }

        public static int getCount() {
            return tagTable.getCount();
        }

        public static int getCountInStock() {
            return tagTable.getCount(inStockOnlyClause);
        }

        public static H2Iterator<Tag> getAllTags(int from, int to) {
            return tagTable.getAll(from, to);
        }

        public static H2Iterator<Tag> getInStockTags(int from, int to) {
            return tagTable.getManyBy(inStockOnlyClause, from, to);
        }

        public static H2Iterator<Tag> getTagsLike(String prefix, boolean inStockOnly, int from, int to) {
            H2Clause h2Clause = new H2ClauseLikeClause("tag", prefix);
            if (inStockOnly) {
                h2Clause = h2Clause.and(inStockOnlyClause);
            }
            return tagTable.getManyBy(h2Clause, from, to, " ORDER BY tag ");
        }

        private static void init() {
        }

        private static void add(Goods goods) {
            for (String tagValue : goods.getParsedTags()) {
                Tag tag = tagTable.get(tagDbKeyFactory.newKey(tagValue));
                if (tag == null) {
                    tag = new Tag(tagValue);
                }
                tag.inStockCount += 1;
                tag.totalCount += 1;
                tagTable.insert(tag);
            }
        }

        private static void delist(Goods goods) {
            for (String tagValue : goods.getParsedTags()) {
                Tag tag = tagTable.get(tagDbKeyFactory.newKey(tagValue));
                if (tag == null) {
                    throw new IllegalStateException("Unknown tag " + tagValue);
                }
                tag.inStockCount -= 1;
                tagTable.insert(tag);
            }
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO tag (tag, in_stock_count, total_count, height, latest) "
                    + "KEY (tag, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setString(++i, this.tag);
                pstmt.setInt(++i, this.inStockCount);
                pstmt.setInt(++i, this.totalCount);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public String getTag() {
            return tag;
        }

        public int getInStockCount() {
            return inStockCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

    }

    public static final class Goods {

        private static final H2KeyLongKeyFactory<Goods> goodsDbKeyFactory = new H2KeyLongKeyFactory<Goods>("Id") {

            @Override
            public H2Key newKey(Goods goods) {
                return goods.h2Key;
            }

        };

        private static final VersionedEntityH2Table<Goods> goodsTable = new VersionedEntityH2Table<Goods>("goods", goodsDbKeyFactory, "name,description,tags") {

            @Override
            protected Goods load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
                return new Goods(rs, h2Key);
            }

            @Override
            protected void save(Connection con, Goods goods) throws SQLException {
                goods.save(con);
            }

            @Override
            protected String defaultSort() {
                return " ORDER BY timestamp DESC, Id ASC ";
            }

        };

        private static final H2Clause inStockClause = new H2ClauseBooleanClause("goods.delisted", false)
                .and(new H2ClauseLongClause("goods.quantity", H2ClauseOp.GT, 0));
        private final long id;
        private final H2Key h2Key;
        private final long sellerId;
        private final String name;
        private final String description;
        private final String tags;
        private final String[] parsedTags;
        private final int timestamp;
        private final boolean hasImage;
        private int quantity;
        private long priceNQT;
        private boolean delisted;
        private Goods(Transaction transaction, Mortgaged.DigitalGoodsListing attachment) {
            this.id = transaction.getTransactionId();
            this.h2Key = goodsDbKeyFactory.newKey(this.id);
            this.sellerId = transaction.getSenderId();
            this.name = attachment.getName();
            this.description = attachment.getDescription();
            this.tags = attachment.getTags();
            this.parsedTags = Search.parseTags(this.tags, 3, 20, 3);
            this.quantity = attachment.getQuantity();
            this.priceNQT = attachment.getPriceNQT();
            this.delisted = false;
            this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
            this.hasImage = transaction.getPrunablePlainMessage() != null;
        }
        private Goods(ResultSet rs, H2Key h2Key) throws SQLException {
            this.id = rs.getLong("Id");
            this.h2Key = h2Key;
            this.sellerId = rs.getLong("seller_id");
            this.name = rs.getString("name");
            this.description = rs.getString("description");
            this.tags = rs.getString("tags");
            this.parsedTags = H2Utils.h2getArray(rs, "parsed_tags", String[].class);
            this.quantity = rs.getInt("quantity");
            this.priceNQT = rs.getLong("price");
            this.delisted = rs.getBoolean("delisted");
            this.timestamp = rs.getInt("timestamp");
            this.hasImage = rs.getBoolean("has_image");
        }

        public static int getCount() {
            return goodsTable.getCount();
        }

        public static int getCountInStock() {
            return goodsTable.getCount(inStockClause);
        }

        public static Goods getGoods(long goodsId) {
            return goodsTable.get(goodsDbKeyFactory.newKey(goodsId));
        }

        public static H2Iterator<Goods> getAllGoods(int from, int to) {
            return goodsTable.getAll(from, to);
        }

        public static H2Iterator<Goods> getGoodsInStock(int from, int to) {
            return goodsTable.getManyBy(inStockClause, from, to);
        }

        public static H2Iterator<Goods> getSellerGoods(final long sellerId, final boolean inStockOnly, int from, int to) {
            return goodsTable.getManyBy(new ElectronicProductStoreSellerH2Clause(sellerId, inStockOnly), from, to, " ORDER BY name ASC, timestamp DESC, Id ASC ");
        }

        public static int getSellerGoodsCount(long sellerId, boolean inStockOnly) {
            return goodsTable.getCount(new ElectronicProductStoreSellerH2Clause(sellerId, inStockOnly));
        }

        public static H2Iterator<Goods> searchGoods(String query, boolean inStockOnly, int from, int to) {
            return goodsTable.searchByGoodsName(query, inStockOnly ? inStockClause : H2Clause.EMPTY_CLAUSE, from, to,
                    " ORDER BY goods.timestamp DESC ");
        }

        public static H2Iterator<Goods> searchSellerGoods(String query, long sellerId, boolean inStockOnly, int from, int to) {
            return goodsTable.search(query, new ElectronicProductStoreSellerH2Clause(sellerId, inStockOnly), from, to,
                    " ORDER BY ft.score DESC, goods.name ASC, goods.timestamp DESC ");
        }

        private static void init() {
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO goods (id, seller_id, name, "
                    + "description, tags, parsed_tags, timestamp, quantity, price, delisted, has_image, height, latest) KEY (id, height) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.sellerId);
                pstmt.setString(++i, this.name);
                pstmt.setString(++i, this.description);
                pstmt.setString(++i, this.tags);
                H2Utils.h2setArray(pstmt, ++i, this.parsedTags);
                pstmt.setInt(++i, this.timestamp);
                pstmt.setInt(++i, this.quantity);
                pstmt.setLong(++i, this.priceNQT);
                pstmt.setBoolean(++i, this.delisted);
                pstmt.setBoolean(++i, this.hasImage);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getSellerId() {
            return sellerId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getTags() {
            return tags;
        }

        public int getTimestamp() {
            return timestamp;
        }

        public int getQuantity() {
            return quantity;
        }

        private void changeQuantity(int deltaQuantity) {
            if (quantity == 0 && deltaQuantity > 0) {
                Tag.add(this);
            }
            quantity += deltaQuantity;
            if (quantity < 0) {
                quantity = 0;
            } else if (quantity > Constants.EC_MAX_DGS_LISTING_QUANTITY) {
                quantity = Constants.EC_MAX_DGS_LISTING_QUANTITY;
            }
            if (quantity == 0) {
                Tag.delist(this);
            }
            goodsTable.insert(this);
        }

        public long getPriceNQT() {
            return priceNQT;
        }

        private void changePrice(long priceNQT) {
            this.priceNQT = priceNQT;
            goodsTable.insert(this);
        }

        public boolean isDelisted() {
            return delisted;
        }

        private void setDelisted(boolean delisted) {
            this.delisted = delisted;
            if (this.quantity > 0) {
                Tag.delist(this);
            }
            goodsTable.insert(this);
        }

        public String[] getParsedTags() {
            return parsedTags;
        }

        public boolean hasImage() {
            return hasImage;
        }

    }

    public static final class Purchase {

        private static final H2KeyLongKeyFactory<Purchase> purchaseDbKeyFactory = new H2KeyLongKeyFactory<Purchase>("Id") {

            @Override
            public H2Key newKey(Purchase purchase) {
                return purchase.h2Key;
            }

        };

        private static final VersionedEntityH2Table<Purchase> purchaseTable = new VersionedEntityH2Table<Purchase>("purchase", purchaseDbKeyFactory) {

            @Override
            protected Purchase load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
                return new Purchase(rs, h2Key);
            }

            @Override
            protected void save(Connection con, Purchase purchase) throws SQLException {
                purchase.save(con);
            }

            @Override
            protected String defaultSort() {
                return " ORDER BY timestamp DESC, Id ASC ";
            }

        };

        private static final H2KeyLongKeyFactory<Purchase> feedbackDbKeyFactory = new H2KeyLongKeyFactory<Purchase>("Id") {

            @Override
            public H2Key newKey(Purchase purchase) {
                return purchase.h2Key == null ? newKey(purchase.id) : purchase.h2Key;
            }

        };

        private static final VersionedValuesH2Table<Purchase, EncryptedData> feedbackTable = new VersionedValuesH2Table<Purchase, EncryptedData>("purchase_feedback", feedbackDbKeyFactory) {

            @Override
            protected EncryptedData load(Connection con, ResultSet rs) throws SQLException {
                byte[] data = rs.getBytes("feedback_data");
                byte[] nonce = rs.getBytes("feedback_nonce");
                return new EncryptedData(data, nonce);
            }

            @Override
            protected void save(Connection con, Purchase purchase, EncryptedData encryptedData) throws SQLException {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO purchase_feedback (id, feedback_data, feedback_nonce, "
                        + "height, latest) VALUES (?, ?, ?, ?, TRUE)")) {
                    int i = 0;
                    pstmt.setLong(++i, purchase.getId());
                    i = setEncryptedData(pstmt, encryptedData, ++i);
                    pstmt.setInt(i, EcBlockchainImpl.getInstance().getHeight());
                    pstmt.executeUpdate();
                }
            }

        };

        private static final H2KeyLongKeyFactory<Purchase> publicFeedbackDbKeyFactory = new H2KeyLongKeyFactory<Purchase>("Id") {

            @Override
            public H2Key newKey(Purchase purchase) {
                return purchase.h2Key == null ? newKey(purchase.id) : purchase.h2Key;
            }

        };

        private static final VersionedValuesH2Table<Purchase, String> publicFeedbackTable = new VersionedValuesH2Table<Purchase, String>("purchase_public_feedback", publicFeedbackDbKeyFactory) {

            @Override
            protected String load(Connection con, ResultSet rs) throws SQLException {
                return rs.getString("public_feedback");
            }

            @Override
            protected void save(Connection con, Purchase purchase, String publicFeedback) throws SQLException {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO purchase_public_feedback (id, public_feedback, "
                        + "height, latest) VALUES (?, ?, ?, TRUE)")) {
                    int i = 0;
                    pstmt.setLong(++i, purchase.getId());
                    pstmt.setString(++i, publicFeedback);
                    pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                    pstmt.executeUpdate();
                }
            }

        };
        private final long id;
        private final H2Key h2Key;
        private final long buyerId;
        private final long goodsId;
        private final long sellerId;
        private final int quantity;
        private final long priceNQT;
        private final int deadline;
        private final EncryptedData note;
        private final int timestamp;
        private boolean isPending;
        private EncryptedData encryptedGoods;
        private boolean goodsIsText;
        private EncryptedData refundNote;
        private boolean hasFeedbackNotes;
        private List<EncryptedData> feedbackNotes;
        private boolean hasPublicFeedbacks;
        private List<String> publicFeedbacks;
        private long discountNQT;
        private long refundNQT;

        private Purchase(Transaction transaction, Mortgaged.DigitalGoodsPurchase attachment, long sellerId) {
            this.id = transaction.getTransactionId();
            this.h2Key = purchaseDbKeyFactory.newKey(this.id);
            this.buyerId = transaction.getSenderId();
            this.goodsId = attachment.getGoodsId();
            this.sellerId = sellerId;
            this.quantity = attachment.getQuantity();
            this.priceNQT = attachment.getPriceNQT();
            this.deadline = attachment.getDeliveryDeadlineTimestamp();
            this.note = transaction.getEncryptedMessage() == null ? null : transaction.getEncryptedMessage().getEncryptedData();
            this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
            this.isPending = true;
        }


        private Purchase(ResultSet rs, H2Key h2Key) throws SQLException {
            this.id = rs.getLong("Id");
            this.h2Key = h2Key;
            this.buyerId = rs.getLong("buyer_id");
            this.goodsId = rs.getLong("goods_id");
            this.sellerId = rs.getLong("seller_id");
            this.quantity = rs.getInt("quantity");
            this.priceNQT = rs.getLong("price");
            this.deadline = rs.getInt("deadline");
            this.note = loadEncryptedData(rs, "note", "nonce");
            this.timestamp = rs.getInt("timestamp");
            this.isPending = rs.getBoolean("pending");
            this.encryptedGoods = loadEncryptedData(rs, "goods", "goods_nonce");
            this.refundNote = loadEncryptedData(rs, "refund_note", "refund_nonce");
            this.hasFeedbackNotes = rs.getBoolean("has_feedback_notes");
            this.hasPublicFeedbacks = rs.getBoolean("has_public_feedbacks");
            this.discountNQT = rs.getLong("discount");
            this.refundNQT = rs.getLong("refund");
            this.goodsIsText = rs.getBoolean("goods_is_text");
        }

        public static int getCount() {
            return purchaseTable.getCount();
        }

        public static int getCount(boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new PurchasesClause(" TRUE ", withPublicFeedbacksOnly, completedOnly));
        }

        public static H2Iterator<Purchase> getAllPurchases(int from, int to) {
            return purchaseTable.getAll(from, to);
        }

        public static H2Iterator<Purchase> getPurchases(boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new PurchasesClause(" TRUE ", withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static H2Iterator<Purchase> getSellerPurchases(long sellerId, boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new LongPurchasesClause("seller_id", sellerId, withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static int getSellerPurchaseCount(long sellerId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new LongPurchasesClause("seller_id", sellerId, withPublicFeedbacksOnly, completedOnly));
        }

        public static H2Iterator<Purchase> getBuyerPurchases(long buyerId, boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new LongPurchasesClause("buyer_id", buyerId, withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static int getBuyerPurchaseCount(long buyerId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new LongPurchasesClause("buyer_id", buyerId, withPublicFeedbacksOnly, completedOnly));
        }

        public static H2Iterator<Purchase> getSellerBuyerPurchases(final long sellerId, final long buyerId,
                                                                   boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            return purchaseTable.getManyBy(new SellerBuyerPurchasesClause(sellerId, buyerId, withPublicFeedbacksOnly, completedOnly), from, to);
        }

        public static int getSellerBuyerPurchaseCount(final long sellerId, final long buyerId,
                                                      boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new SellerBuyerPurchasesClause(sellerId, buyerId, withPublicFeedbacksOnly, completedOnly));
        }

        public static H2Iterator<Purchase> getGoodsPurchases(long goodsId, long buyerId, boolean withPublicFeedbacksOnly, boolean completedOnly, int from, int to) {
            H2Clause clause = new LongPurchasesClause("goods_id", goodsId, withPublicFeedbacksOnly, completedOnly);
            if (buyerId != 0) {
                clause = clause.and(new H2ClauseLongClause("buyer_id", buyerId));
            }
            return purchaseTable.getManyBy(clause, from, to);
        }

        public static int getGoodsPurchaseCount(final long goodsId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
            return purchaseTable.getCount(new LongPurchasesClause("goods_id", goodsId, withPublicFeedbacksOnly, completedOnly));
        }

        public static Purchase getPurchase(long purchaseId) {
            return purchaseTable.get(purchaseDbKeyFactory.newKey(purchaseId));
        }

        public static H2Iterator<Purchase> getPendingSellerPurchases(final long sellerId, int from, int to) {
            H2Clause h2Clause = new H2ClauseLongClause("seller_id", sellerId).and(new H2ClauseBooleanClause("pending", true));
            return purchaseTable.getManyBy(h2Clause, from, to);
        }

        public static H2Iterator<Purchase> getExpiredSellerPurchases(final long sellerId, int from, int to) {
            H2Clause h2Clause = new H2ClauseLongClause("seller_id", sellerId)
                    .and(new H2ClauseBooleanClause("pending", false))
                    .and(new H2ClauseNullClause("goods"));
            return purchaseTable.getManyBy(h2Clause, from, to);
        }

        static Purchase getPendingPurchase(long purchaseId) {
            Purchase purchase = getPurchase(purchaseId);
            return purchase == null || !purchase.isPending() ? null : purchase;
        }

        private static H2Iterator<Purchase> getExpiredPendingPurchases(EcBlock ecBlock) {
            final int timestamp = ecBlock.getTimestamp();
            final int previousTimestamp = EcBlockchainImpl.getInstance().getBlock(ecBlock.getPreviousBlockId()).getTimestamp();
            H2Clause h2Clause = new H2ClauseLongClause("deadline", H2ClauseOp.LT, timestamp)
                    .and(new H2ClauseLongClause("deadline", H2ClauseOp.GTE, previousTimestamp))
                    .and(new H2ClauseBooleanClause("pending", true));
            return purchaseTable.getManyBy(h2Clause, 0, -1);
        }

        private static void init() {
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO purchase (id, buyer_id, goods_id, seller_id, "
                    + "quantity, price, deadline, note, nonce, timestamp, pending, goods, goods_nonce, goods_is_text, refund_note, "
                    + "refund_nonce, has_feedback_notes, has_public_feedbacks, discount, refund, height, latest) KEY (id, height) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.buyerId);
                pstmt.setLong(++i, this.goodsId);
                pstmt.setLong(++i, this.sellerId);
                pstmt.setInt(++i, this.quantity);
                pstmt.setLong(++i, this.priceNQT);
                pstmt.setInt(++i, this.deadline);
                i = setEncryptedData(pstmt, this.note, ++i);
                pstmt.setInt(i, this.timestamp);
                pstmt.setBoolean(++i, this.isPending);
                i = setEncryptedData(pstmt, this.encryptedGoods, ++i);
                pstmt.setBoolean(i, this.goodsIsText);
                i = setEncryptedData(pstmt, this.refundNote, ++i);
                pstmt.setBoolean(i, this.hasFeedbackNotes);
                pstmt.setBoolean(++i, this.hasPublicFeedbacks);
                pstmt.setLong(++i, this.discountNQT);
                pstmt.setLong(++i, this.refundNQT);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getBuyerId() {
            return buyerId;
        }

        public long getGoodsId() {
            return goodsId;
        }

        public long getSellerId() {
            return sellerId;
        }

        public int getQuantity() {
            return quantity;
        }

        public long getPriceNQT() {
            return priceNQT;
        }

        public int getDeliveryDeadlineTimestamp() {
            return deadline;
        }

        public EncryptedData getNote() {
            return note;
        }

        public boolean isPending() {
            return isPending;
        }

        private void setPending(boolean isPending) {
            this.isPending = isPending;
            purchaseTable.insert(this);
        }

        public int getTimestamp() {
            return timestamp;
        }

        public EncryptedData getEncryptedGoods() {
            return encryptedGoods;
        }

        public boolean goodsIsText() {
            return goodsIsText;
        }

        private void setEncryptedGoods(EncryptedData encryptedGoods, boolean goodsIsText) {
            this.encryptedGoods = encryptedGoods;
            this.goodsIsText = goodsIsText;
            purchaseTable.insert(this);
        }

        public EncryptedData getRefundNote() {
            return refundNote;
        }

        private void setRefundNote(EncryptedData refundNote) {
            this.refundNote = refundNote;
            purchaseTable.insert(this);
        }

        public boolean hasFeedbackNotes() {
            return hasFeedbackNotes;
        }

        public List<EncryptedData> getFeedbackNotes() {
            if (!hasFeedbackNotes) {
                return null;
            }
            feedbackNotes = feedbackTable.get(feedbackDbKeyFactory.newKey(this));
            return feedbackNotes;
        }

        private void addFeedbackNote(EncryptedData feedbackNote) {
            if (getFeedbackNotes() == null) {
                feedbackNotes = new ArrayList<>();
            }
            feedbackNotes.add(feedbackNote);
            if (!this.hasFeedbackNotes) {
                this.hasFeedbackNotes = true;
                purchaseTable.insert(this);
            }
            feedbackTable.insert(this, feedbackNotes);
        }

        public boolean hasPublicFeedbacks() {
            return hasPublicFeedbacks;
        }

        public List<String> getPublicFeedbacks() {
            if (!hasPublicFeedbacks) {
                return null;
            }
            publicFeedbacks = publicFeedbackTable.get(publicFeedbackDbKeyFactory.newKey(this));
            return publicFeedbacks;
        }

        private void addPublicFeedback(String publicFeedback) {
            if (getPublicFeedbacks() == null) {
                publicFeedbacks = new ArrayList<>();
            }
            publicFeedbacks.add(publicFeedback);
            if (!this.hasPublicFeedbacks) {
                this.hasPublicFeedbacks = true;
                purchaseTable.insert(this);
            }
            publicFeedbackTable.insert(this, publicFeedbacks);
        }

        public long getDiscountNQT() {
            return discountNQT;
        }

        private void setDiscountNQT(long discountNQT) {
            this.discountNQT = discountNQT;
            purchaseTable.insert(this);
        }

        public long getRefundNQT() {
            return refundNQT;
        }

        private void setRefundNQT(long refundNQT) {
            this.refundNQT = refundNQT;
            purchaseTable.insert(this);
        }

        private static class PurchasesClause extends H2Clause {

            private PurchasesClause(String clause, boolean withPublicFeedbacksOnly, boolean completedOnly) {
                super(clause + (completedOnly ? " AND goods IS NOT NULL " : " ")
                        + (withPublicFeedbacksOnly ? " AND has_public_feedbacks = TRUE " : " "));
            }

            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                return index;
            }

        }

        private static final class LongPurchasesClause extends PurchasesClause {

            private final long value;

            private LongPurchasesClause(String columnName, long value, boolean withPublicFeedbacksOnly, boolean completedOnly) {
                super(columnName + " = ? ", withPublicFeedbacksOnly, completedOnly);
                this.value = value;
            }

            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                pstmt.setLong(index++, value);
                return index;
            }

        }

        private static final class SellerBuyerPurchasesClause extends PurchasesClause {

            private final long sellerId;
            private final long buyerId;

            private SellerBuyerPurchasesClause(long sellerId, long buyerId, boolean withPublicFeedbacksOnly, boolean completedOnly) {
                super(" seller_id = ? AND buyer_id = ? ", withPublicFeedbacksOnly, completedOnly);
                this.sellerId = sellerId;
                this.buyerId = buyerId;
            }

            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                pstmt.setLong(index++, sellerId);
                pstmt.setLong(index++, buyerId);
                return index;
            }

        }

    }

}
