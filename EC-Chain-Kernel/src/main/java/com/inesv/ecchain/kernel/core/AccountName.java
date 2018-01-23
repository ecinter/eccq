package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AccountName {

    private static final H2KeyLongKeyFactory<AccountName> ALIAS_DB_KEY_FACTORY = new H2KeyLongKeyFactory<AccountName>("Id") {

        @Override
        public H2Key newKey(AccountName accountName) {
            return accountName.h2Key;
        }

    };
    private static final VersionedEntityH2Table<AccountName> ALIAS_TABLE = new VersionedEntityH2Table<AccountName>("alias", ALIAS_DB_KEY_FACTORY) {

        @Override
        protected AccountName load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new AccountName(rs, h2Key);
        }

        @Override
        protected void save(Connection con, AccountName accountName) throws SQLException {
            accountName.save(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY alias_name_lower ";
        }

    };
    private static final H2KeyLongKeyFactory<Offer> OFFER_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Offer>("Id") {

        @Override
        public H2Key newKey(Offer offer) {
            return offer.h2Key;
        }

    };
    private static final VersionedEntityH2Table<Offer> OFFER_TABLE = new VersionedEntityH2Table<Offer>("alias_offer", OFFER_DB_KEY_FACTORY) {

        @Override
        protected Offer load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Offer(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Offer offer) throws SQLException {
            offer.save(con);
        }

    };
    private final long id;
    private final H2Key h2Key;
    private final String propertysName;
    private long accountId;
    private String propertysURI;
    private int timestamp;

    private AccountName(Transaction transaction, Mortgaged.MessagingAliasAssignment attachment) {
        this.id = transaction.getTransactionId();
        this.h2Key = ALIAS_DB_KEY_FACTORY.newKey(this.id);
        this.accountId = transaction.getSenderId();
        this.propertysName = attachment.getAliasName();
        this.propertysURI = attachment.getAliasURI();
        this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
    }

    private AccountName(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.accountId = rs.getLong("account_id");
        this.propertysName = rs.getString("alias_name");
        this.propertysURI = rs.getString("alias_uri");
        this.timestamp = rs.getInt("timestamp");
    }

    public static int getCount() {
        return ALIAS_TABLE.getCount();
    }

    public static int getAccountPropertysCount(long accountId) {
        return ALIAS_TABLE.getCount(new H2ClauseLongClause("account_id", accountId));
    }

    public static H2Iterator<AccountName> getAliasesByOwner(long accountId, int from, int to) {
        return ALIAS_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
    }

    public static AccountName getAlias(String aliasName) {
        return ALIAS_TABLE.getBy(new H2ClauseStringClause("alias_name_lower", aliasName.toLowerCase()));
    }

    public static H2Iterator<AccountName> getAliasesLike(String aliasName, int from, int to) {
        return ALIAS_TABLE.getManyBy(new H2ClauseLikeClause("alias_name_lower", aliasName.toLowerCase()), from, to);
    }

    public static AccountName getAlias(long id) {
        return ALIAS_TABLE.get(ALIAS_DB_KEY_FACTORY.newKey(id));
    }

    public static Offer getOffer(AccountName accountName) {
        return OFFER_TABLE.get(OFFER_DB_KEY_FACTORY.newKey(accountName.getId()));
    }

    static void deleteAlias(final String aliasName) {
        final AccountName accountName = getAlias(aliasName);
        final Offer offer = AccountName.getOffer(accountName);
        if (offer != null) {
            OFFER_TABLE.delete(offer);
        }
        ALIAS_TABLE.delete(accountName);
    }

    static void addOrUpdateAlias(Transaction transaction, Mortgaged.MessagingAliasAssignment attachment) {
        AccountName accountName = getAlias(attachment.getAliasName());
        if (accountName == null) {
            accountName = new AccountName(transaction, attachment);
        } else {
            accountName.accountId = transaction.getSenderId();
            accountName.propertysURI = attachment.getAliasURI();
            accountName.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
        }
        ALIAS_TABLE.insert(accountName);
    }

    static void sellAlias(Transaction transaction, Mortgaged.MessagingAliasSell attachment) {
        final String aliasName = attachment.getAliasName();
        final long priceNQT = attachment.getPriceNQT();
        final long buyerId = transaction.getRecipientId();
        if (priceNQT > 0) {
            AccountName accountName = getAlias(aliasName);
            Offer offer = getOffer(accountName);
            if (offer == null) {
                OFFER_TABLE.insert(new Offer(accountName.id, priceNQT, buyerId));
            } else {
                offer.priceNQT = priceNQT;
                offer.buyerId = buyerId;
                OFFER_TABLE.insert(offer);
            }
        } else {
            changeOwner(buyerId, aliasName);
        }

    }

    static void changeOwner(long newOwnerId, String aliasName) {
        AccountName accountName = getAlias(aliasName);
        accountName.accountId = newOwnerId;
        accountName.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
        ALIAS_TABLE.insert(accountName);
        Offer offer = getOffer(accountName);
        OFFER_TABLE.delete(offer);
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO alias (id, account_id, alias_name, "
                + "alias_uri, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setString(++i, this.propertysName);
            pstmt.setString(++i, this.propertysURI);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public String getPropertysName() {
        return propertysName;
    }

    public String getPropertysURI() {
        return propertysURI;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public long getAccountId() {
        return accountId;
    }

    public static class Offer {

        private final long aliasId;
        private final H2Key h2Key;
        private long priceNQT;
        private long buyerId;

        private Offer(long aliasId, long priceNQT, long buyerId) {
            this.priceNQT = priceNQT;
            this.buyerId = buyerId;
            this.aliasId = aliasId;
            this.h2Key = OFFER_DB_KEY_FACTORY.newKey(this.aliasId);
        }

        private Offer(ResultSet rs, H2Key h2Key) throws SQLException {
            this.aliasId = rs.getLong("Id");
            this.h2Key = h2Key;
            this.priceNQT = rs.getLong("price");
            this.buyerId = rs.getLong("buyer_id");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO alias_offer (id, price, buyer_id, "
                    + "height) VALUES (?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, this.aliasId);
                pstmt.setLong(++i, this.priceNQT);
                H2Utils.h2setLongZeroToNull(pstmt, ++i, this.buyerId);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return aliasId;
        }

        public long getPriceNQT() {
            return priceNQT;
        }

        public long getBuyerId() {
            return buyerId;
        }

    }
}
