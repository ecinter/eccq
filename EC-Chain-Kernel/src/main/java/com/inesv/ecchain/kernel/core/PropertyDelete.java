package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PropertyDelete {

    private static final ListenerManager<PropertyDelete, Event> PROPERTY_DELETE_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLongKeyFactory<PropertyDelete> DELETE_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PropertyDelete>("Id") {

        @Override
        public H2Key newKey(PropertyDelete propertyDelete) {
            return propertyDelete.h2Key;
        }

    };
    private static final EntityH2Table<PropertyDelete> ASSET_DELETE_TABLE = new EntityH2Table<PropertyDelete>("asset_delete", DELETE_DB_KEY_FACTORY) {

        @Override
        protected PropertyDelete load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PropertyDelete(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PropertyDelete propertyDelete) throws SQLException {
            propertyDelete.save(con);
        }

    };
    private final long id;
    private final H2Key h2Key;
    private final long assetId;
    private final int height;
    private final long accountId;
    private final long quantityQNT;
    private final int timestamp;

    private PropertyDelete(Transaction transaction, long assetId, long quantityQNT) {
        this.id = transaction.getTransactionId();
        this.h2Key = DELETE_DB_KEY_FACTORY.newKey(this.id);
        this.assetId = assetId;
        this.accountId = transaction.getSenderId();
        this.quantityQNT = quantityQNT;
        this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
        this.height = EcBlockchainImpl.getInstance().getHeight();
    }


    private PropertyDelete(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.assetId = rs.getLong("asset_id");
        this.accountId = rs.getLong("account_id");
        this.quantityQNT = rs.getLong("quantity");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
    }

    public static H2Iterator<PropertyDelete> getAssetDeletes(long assetId, int from, int to) {
        return ASSET_DELETE_TABLE.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to);
    }

    public static H2Iterator<PropertyDelete> getAccountAssetDeletes(long accountId, int from, int to) {
        return ASSET_DELETE_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to, " ORDER BY height DESC, db_id DESC ");
    }

    public static H2Iterator<PropertyDelete> getAccountAssetDeletes(long accountId, long assetId, int from, int to) {
        return ASSET_DELETE_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId).and(new H2ClauseLongClause("asset_id", assetId)),
                from, to, " ORDER BY height DESC, db_id DESC ");
    }

    static PropertyDelete addAssetDelete(Transaction transaction, long assetId, long quantityQNT) {
        PropertyDelete propertyDelete = new PropertyDelete(transaction, assetId, quantityQNT);
        ASSET_DELETE_TABLE.insert(propertyDelete);
        PROPERTY_DELETE_EVENT_LISTENER_MANAGER.notify(propertyDelete, Event.ASSET_DELETE);
        return propertyDelete;
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_delete (id, asset_id, "
                + "account_id, quantity, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.accountId);
            pstmt.setLong(++i, this.quantityQNT);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, this.height);
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getAssetId() {
        return assetId;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getQuantityQNT() {
        return quantityQNT;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

    public enum Event {
        ASSET_DELETE
    }

}
