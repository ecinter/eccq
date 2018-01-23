package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CoinTransfer {

    private static final ListenerManager<CoinTransfer, CoinTransferEvent> COIN_TRANSFER_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLongKeyFactory<CoinTransfer> CURRENCY_TRANSFER_DB_KEY_FACTORY = new H2KeyLongKeyFactory<CoinTransfer>("Id") {

        @Override
        public H2Key newKey(CoinTransfer transfer) {
            return transfer.h2Key;
        }

    };
    private static final EntityH2Table<CoinTransfer> CURRENCY_TRANSFER_TABLE = new EntityH2Table<CoinTransfer>("currency_transfer", CURRENCY_TRANSFER_DB_KEY_FACTORY) {

        @Override
        protected CoinTransfer load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new CoinTransfer(rs, h2Key);
        }

        @Override
        protected void save(Connection con, CoinTransfer transfer) throws SQLException {
            transfer.save(con);
        }

    };
    private final long id;
    private final H2Key h2Key;
    private final long currencyId;
    private final int height;
    private final long senderId;
    private final long recipientId;
    private final long units;
    private final int timestamp;

    private CoinTransfer(Transaction transaction, Mortgaged.MonetarySystemCurrencyTransfer attachment) {
        this.id = transaction.getTransactionId();
        this.h2Key = CURRENCY_TRANSFER_DB_KEY_FACTORY.newKey(this.id);
        this.height = EcBlockchainImpl.getInstance().getHeight();
        this.currencyId = attachment.getCurrencyId();
        this.senderId = transaction.getSenderId();
        this.recipientId = transaction.getRecipientId();
        this.units = attachment.getUnits();
        this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
    }

    private CoinTransfer(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.currencyId = rs.getLong("currency_id");
        this.senderId = rs.getLong("sender_id");
        this.recipientId = rs.getLong("recipient_id");
        this.units = rs.getLong("units");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
    }

    public static int getCount() {
        return CURRENCY_TRANSFER_TABLE.getCount();
    }

    public static H2Iterator<CoinTransfer> getCoinTransfers(long currencyId, int from, int to) {
        return CURRENCY_TRANSFER_TABLE.getManyBy(new H2ClauseLongClause("currency_id", currencyId), from, to);
    }

    public static H2Iterator<CoinTransfer> getAccountCoinTransfers(long accountId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM currency_transfer WHERE sender_id = ?"
                    + " UNION ALL SELECT * FROM currency_transfer WHERE recipient_id = ? AND sender_id <> ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return CURRENCY_TRANSFER_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<CoinTransfer> getAccountCoinTransfers(long accountId, long currencyId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM currency_transfer WHERE sender_id = ? AND currency_id = ?"
                    + " UNION ALL SELECT * FROM currency_transfer WHERE recipient_id = ? AND sender_id <> ? AND currency_id = ? ORDER BY height DESC, db_id DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, currencyId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, currencyId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return CURRENCY_TRANSFER_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static int getTransferCount(long currencyId) {
        return CURRENCY_TRANSFER_TABLE.getCount(new H2ClauseLongClause("currency_id", currencyId));
    }

    static CoinTransfer addTransfer(Transaction transaction, Mortgaged.MonetarySystemCurrencyTransfer attachment) {
        CoinTransfer transfer = new CoinTransfer(transaction, attachment);
        CURRENCY_TRANSFER_TABLE.insert(transfer);
        COIN_TRANSFER_EVENT_LISTENER_MANAGER.notify(transfer, CoinTransferEvent.TRANSFER);
        return transfer;
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO currency_transfer (id, currency_id, "
                + "sender_id, recipient_id, units, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.currencyId);
            pstmt.setLong(++i, this.senderId);
            pstmt.setLong(++i, this.recipientId);
            pstmt.setLong(++i, this.units);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, this.height);
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getCurrencyId() {
        return currencyId;
    }

    public long getSenderId() {
        return senderId;
    }

    public long getRecipientId() {
        return recipientId;
    }

    public long getUnits() {
        return units;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

}
