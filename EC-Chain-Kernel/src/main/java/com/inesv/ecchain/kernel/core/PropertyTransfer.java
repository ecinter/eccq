package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.Listener;
import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PropertyTransfer {

    private static final ListenerManager<PropertyTransfer, Event> PROPERTY_TRANSFER_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLongKeyFactory<PropertyTransfer> TRANSFER_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PropertyTransfer>("Id") {

        @Override
        public H2Key newKey(PropertyTransfer propertyTransfer) {
            return propertyTransfer.h2Key;
        }

    };
    private static final EntityH2Table<PropertyTransfer> ASSET_TRANSFER_TABLE = new EntityH2Table<PropertyTransfer>("asset_transfer", TRANSFER_DB_KEY_FACTORY) {

        @Override
        protected PropertyTransfer load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PropertyTransfer(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PropertyTransfer propertyTransfer) throws SQLException {
            propertyTransfer.save(con);
        }

    };
    private final long id;
    private final H2Key h2Key;
    private final long assetId;
    private final int height;
    private final long senderId;
    private final long recipientId;
    private final long quantityQNT;
    private final int timestamp;

    private PropertyTransfer(Transaction transaction, Mortgaged.ColoredCoinsAssetTransfer attachment) {
        this.id = transaction.getTransactionId();
        this.h2Key = TRANSFER_DB_KEY_FACTORY.newKey(this.id);
        this.height = EcBlockchainImpl.getInstance().getHeight();
        this.assetId = attachment.getAssetId();
        this.senderId = transaction.getSenderId();
        this.recipientId = transaction.getRecipientId();
        this.quantityQNT = attachment.getQuantityQNT();
        this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
    }

    private PropertyTransfer(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.assetId = rs.getLong("asset_id");
        this.senderId = rs.getLong("sender_id");
        this.recipientId = rs.getLong("recipient_id");
        this.quantityQNT = rs.getLong("quantity");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
    }

    public static int getCount() {
        return ASSET_TRANSFER_TABLE.getCount();
    }

    public static boolean addListener(Listener<PropertyTransfer> listener, Event eventType) {
        return PROPERTY_TRANSFER_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removeListener(Listener<PropertyTransfer> listener, Event eventType) {
        return PROPERTY_TRANSFER_EVENT_LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static H2Iterator<PropertyTransfer> getAssetTransfers(long assetId, int from, int to) {
        return ASSET_TRANSFER_TABLE.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to);
    }

    public static H2Iterator<PropertyTransfer> getAccountAssetTransfers(long accountId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM asset_transfer WHERE sender_id = ?"
                    + " UNION ALL SELECT * FROM asset_transfer WHERE recipient_id = ? AND sender_id <> ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return ASSET_TRANSFER_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<PropertyTransfer> getAccountAssetTransfers(long accountId, long assetId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM asset_transfer WHERE sender_id = ? AND asset_id = ?"
                    + " UNION ALL SELECT * FROM asset_transfer WHERE recipient_id = ? AND sender_id <> ? AND asset_id = ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, assetId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, assetId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return ASSET_TRANSFER_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static int getTransferCount(long assetId) {
        return ASSET_TRANSFER_TABLE.getCount(new H2ClauseLongClause("asset_id", assetId));
    }

    static PropertyTransfer addAssetTransfer(Transaction transaction, Mortgaged.ColoredCoinsAssetTransfer attachment) {
        PropertyTransfer propertyTransfer = new PropertyTransfer(transaction, attachment);
        ASSET_TRANSFER_TABLE.insert(propertyTransfer);
        PROPERTY_TRANSFER_EVENT_LISTENER_MANAGER.notify(propertyTransfer, Event.ASSET_TRANSFER);
        return propertyTransfer;
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_transfer (id, asset_id, "
                + "sender_id, recipient_id, quantity, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.senderId);
            pstmt.setLong(++i, this.recipientId);
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

    public long getSenderId() {
        return senderId;
    }

    public long getRecipientId() {
        return recipientId;
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
        ASSET_TRANSFER
    }

}
