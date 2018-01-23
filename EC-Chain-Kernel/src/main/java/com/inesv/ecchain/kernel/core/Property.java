package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Property {

    private static final H2KeyLongKeyFactory<Property> ASSET_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Property>("Id") {

        @Override
        public H2Key newKey(Property property) {
            return property.h2Key;
        }

    };

    private static final VersionedEntityH2Table<Property> ASSET_TABLE = new VersionedEntityH2Table<Property>("asset", ASSET_DB_KEY_FACTORY, "name,description") {

        @Override
        protected Property load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Property(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Property property) throws SQLException {
            property.saveProperty(con);
        }

        @Override
        public void trim(int height) {
            super.trim(Math.max(0, height - Constants.EC_MAX_DIVIDEND_PAYMENT_ROLLBACK));
        }

        @Override
        public void checkAvailable(int height) {
            if (height + Constants.EC_MAX_DIVIDEND_PAYMENT_ROLLBACK < EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight()) {
                throw new IllegalArgumentException("Historical data as of height " + height + " not available.");
            }
            if (height > EcBlockchainImpl.getInstance().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " exceeds EC_BLOCKCHAIN height " + EcBlockchainImpl.getInstance().getHeight());
            }
        }

    };
    private final long assetId;
    private final H2Key h2Key;
    private final long accountId;
    private final String name;
    private final String description;
    private final long initialQuantityQNT;
    private final byte decimals;
    private long quantityQNT;

    private Property(Transaction transaction, Mortgaged.ColoredCoinsAssetIssuance attachment) {
        this.assetId = transaction.getTransactionId();
        this.h2Key = ASSET_DB_KEY_FACTORY.newKey(this.assetId);
        this.accountId = transaction.getSenderId();
        this.name = attachment.getName();
        this.description = attachment.getDescription();
        this.quantityQNT = attachment.getQuantityQNT();
        this.initialQuantityQNT = this.quantityQNT;
        this.decimals = attachment.getDecimals();
    }


    private Property(ResultSet rs, H2Key h2Key) throws SQLException {
        this.assetId = rs.getLong("Id");
        this.h2Key = h2Key;
        this.accountId = rs.getLong("account_id");
        this.name = rs.getString("name");
        this.description = rs.getString("description");
        this.initialQuantityQNT = rs.getLong("initial_quantity");
        this.quantityQNT = rs.getLong("quantity");
        this.decimals = rs.getByte("decimals");
    }

    public static H2Iterator<Property> getAllAssets(int from, int to) {
        return ASSET_TABLE.getAll(from, to);
    }

    public static int getCount() {
        return ASSET_TABLE.getCount();
    }

    public static Property getAsset(long id) {
        return ASSET_TABLE.get(ASSET_DB_KEY_FACTORY.newKey(id));
    }

    public static Property getAsset(long id, int height) {
        return ASSET_TABLE.get(ASSET_DB_KEY_FACTORY.newKey(id), height);
    }

    public static H2Iterator<Property> getPropertysIssuedBy(long accountId, int from, int to) {
        return ASSET_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
    }

    public static H2Iterator<Property> searchAssets(String query, int from, int to) {
        return ASSET_TABLE.search(query, H2Clause.EMPTY_CLAUSE, from, to, " ORDER BY ft.score DESC ");
    }

    static void addProperty(Transaction transaction, Mortgaged.ColoredCoinsAssetIssuance attachment) {
        ASSET_TABLE.insert(new Property(transaction, attachment));
    }

    static void deleteProperty(Transaction transaction, long assetId, long quantityQNT) {
        Property property = getAsset(assetId);
        property.quantityQNT = Math.max(0, property.quantityQNT - quantityQNT);
        ASSET_TABLE.insert(property);
        PropertyDelete.addAssetDelete(transaction, assetId, quantityQNT);
    }

    public static void start() {
    }

    private void saveProperty(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO asset "
                + "(id, account_id, name, description, initial_quantity, quantity, decimals, height, latest) "
                + "KEY(id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.accountId);
            pstmt.setString(++i, this.name);
            pstmt.setString(++i, this.description);
            pstmt.setLong(++i, this.initialQuantityQNT);
            pstmt.setLong(++i, this.quantityQNT);
            pstmt.setByte(++i, this.decimals);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return assetId;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getInitialQuantityQNT() {
        return initialQuantityQNT;
    }

    public long getQuantityQNT() {
        return quantityQNT;
    }

    public byte getDecimals() {
        return decimals;
    }
}
