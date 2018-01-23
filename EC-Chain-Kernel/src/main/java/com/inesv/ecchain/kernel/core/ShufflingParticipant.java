package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ShufflingParticipant {

    private static final ListenerManager<ShufflingParticipant, ShufflingParticipantEvent> LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLinkKeyFactory<ShufflingParticipant> SHUFFLING_PARTICIPANT_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<ShufflingParticipant>("shuffling_id", "account_id") {

        @Override
        public H2Key newKey(ShufflingParticipant participant) {
            return participant.h2Key;
        }

    };
    private static final VersionedEntityH2Table<ShufflingParticipant> SHUFFLING_PARTICIPANT_TABLE = new VersionedEntityH2Table<ShufflingParticipant>("shuffling_participant", SHUFFLING_PARTICIPANT_DB_KEY_FACTORY) {

        @Override
        protected ShufflingParticipant load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new ShufflingParticipant(rs, h2Key);
        }

        @Override
        protected void save(Connection con, ShufflingParticipant participant) throws SQLException {
            participant.saveShufflingParticipant(con);
        }

    };
    private static final H2KeyLinkKeyFactory<ShufflingData> SHUFFLING_DATA_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<ShufflingData>("shuffling_id", "account_id") {

        @Override
        public H2Key newKey(ShufflingData shufflingData) {
            return shufflingData.h2Key;
        }

    };
    private static final PrunableH2Table<ShufflingData> SHUFFLING_DATA_TABLE = new PrunableH2Table<ShufflingData>("shuffling_data", SHUFFLING_DATA_DB_KEY_FACTORY) {

        @Override
        protected ShufflingData load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new ShufflingData(rs, h2Key);
        }

        @Override
        protected void save(Connection con, ShufflingData shufflingData) throws SQLException {
            shufflingData.save(con);
        }

    };
    private final long shufflingId;
    private final long accountId; // sender account
    private final H2Key h2Key;
    private final int index;
    private long nextAccountId; // pointer to the next shuffling participant updated during registration
    private ShufflingParticipantState state; // tracks the state of the participant in the process
    private byte[][] blameData; // encrypted data saved as intermediate result in the shuffling process
    private byte[][] keySeeds; // to be revealed only if shuffle is being cancelled
    private byte[] dataTransactionFullHash;

    private ShufflingParticipant(long shufflingId, long accountId, int index) {
        this.shufflingId = shufflingId;
        this.accountId = accountId;
        this.h2Key = SHUFFLING_PARTICIPANT_DB_KEY_FACTORY.newKey(shufflingId, accountId);
        this.index = index;
        this.state = ShufflingParticipantState.REGISTERED;
        this.blameData = Convert.EC_EMPTY_BYTES;
        this.keySeeds = Convert.EC_EMPTY_BYTES;
    }

    private ShufflingParticipant(ResultSet rs, H2Key h2Key) throws SQLException {
        this.shufflingId = rs.getLong("shuffling_id");
        this.accountId = rs.getLong("account_id");
        this.h2Key = h2Key;
        this.nextAccountId = rs.getLong("next_account_id");
        this.index = rs.getInt("participant_index");
        this.state = ShufflingParticipantState.get(rs.getByte("state"));
        this.blameData = H2Utils.h2getArray(rs, "blame_data", byte[][].class, Convert.EC_EMPTY_BYTES);
        this.keySeeds = H2Utils.h2getArray(rs, "key_seeds", byte[][].class, Convert.EC_EMPTY_BYTES);
        this.dataTransactionFullHash = rs.getBytes("data_transaction_full_hash");
    }

    public static H2Iterator<ShufflingParticipant> getParticipants(long shufflingId) {
        return SHUFFLING_PARTICIPANT_TABLE.getManyBy(new H2ClauseLongClause("shuffling_id", shufflingId), 0, -1, " ORDER BY participant_index ");
    }

    public static ShufflingParticipant getParticipant(long shufflingId, long accountId) {
        return SHUFFLING_PARTICIPANT_TABLE.get(SHUFFLING_PARTICIPANT_DB_KEY_FACTORY.newKey(shufflingId, accountId));
    }

    static ShufflingParticipant getLastParticipant(long shufflingId) {
        return SHUFFLING_PARTICIPANT_TABLE.getBy(new H2ClauseLongClause("shuffling_id", shufflingId).and(new H2ClauseNullClause("next_account_id")));
    }

    static void addParticipant(long shufflingId, long accountId, int index) {
        ShufflingParticipant participant = new ShufflingParticipant(shufflingId, accountId, index);
        SHUFFLING_PARTICIPANT_TABLE.insert(participant);
        LISTENER_MANAGER.notify(participant, ShufflingParticipantEvent.PARTICIPANT_REGISTERED);
    }

    static int getVerifiedCount(long shufflingId) {
        return SHUFFLING_PARTICIPANT_TABLE.getCount(new H2ClauseLongClause("shuffling_id", shufflingId).and(
                new H2ClauseByteClause("state", ShufflingParticipantState.VERIFIED.getCode())));
    }

    public static void start() {
    }

    static byte[][] getData(long shufflingId, long accountId) {
        ShufflingData shufflingData = SHUFFLING_DATA_TABLE.get(SHUFFLING_DATA_DB_KEY_FACTORY.newKey(shufflingId, accountId));
        return shufflingData != null ? shufflingData.data : null;
    }

    static void restoreData(long shufflingId, long accountId, byte[][] data, int timestamp, int height) {
        if (data != null && getData(shufflingId, accountId) == null) {
            SHUFFLING_DATA_TABLE.insert(new ShufflingData(shufflingId, accountId, data, timestamp, height));
        }
    }

    private void saveShufflingParticipant(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO shuffling_participant (shuffling_id, "
                + "account_id, next_account_id, participant_index, state, blame_data, key_seeds, data_transaction_full_hash, height, latest) "
                + "KEY (shuffling_id, account_id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.shufflingId);
            pstmt.setLong(++i, this.accountId);
            H2Utils.h2setLongZeroToNull(pstmt, ++i, this.nextAccountId);
            pstmt.setInt(++i, this.index);
            pstmt.setByte(++i, this.getState().getCode());
            H2Utils.h2setArrayEmptyToNull(pstmt, ++i, this.blameData);
            H2Utils.h2setArrayEmptyToNull(pstmt, ++i, this.keySeeds);
            H2Utils.h2setBytes(pstmt, ++i, this.dataTransactionFullHash);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getShufflingId() {
        return shufflingId;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getNextAccountId() {
        return nextAccountId;
    }

    void setNextAccountId(long nextAccountId) {
        if (this.nextAccountId != 0) {
            throw new IllegalStateException("nextAccountId already set to " + Long.toUnsignedString(this.nextAccountId));
        }
        this.nextAccountId = nextAccountId;
        SHUFFLING_PARTICIPANT_TABLE.insert(this);
    }

    public int getIndex() {
        return index;
    }

    public ShufflingParticipantState getState() {
        return state;
    }

    // caller must update database
    private void setState(ShufflingParticipantState state) {
        if (!this.state.canBecome(state)) {
            throw new IllegalStateException(String.format("Shuffling participant in state %s cannot go to state %s", this.state, state));
        }
        this.state = state;
        LoggerUtil.logInfo("Shuffling participant " + Long.toUnsignedString(accountId) + " changed state to " + this.state);
    }

    public byte[][] getData() {
        return getData(shufflingId, accountId);
    }

    void setData(byte[][] data, int timestamp) {
        if (data != null && new EcTime.EpochEcTime().getTime() - timestamp < Constants.EC_MAX_PRUNABLE_LIFETIME && getData() == null) {
            SHUFFLING_DATA_TABLE.insert(new ShufflingData(shufflingId, accountId, data, timestamp, EcBlockchainImpl.getInstance().getHeight()));
        }
    }

    public byte[][] getBlameData() {
        return blameData;
    }

    public byte[][] getKeySeeds() {
        return keySeeds;
    }

    void cancel(byte[][] blameData, byte[][] keySeeds) {
        if (this.keySeeds.length > 0) {
            throw new IllegalStateException("keySeeds already set");
        }
        this.blameData = blameData;
        this.keySeeds = keySeeds;
        setState(ShufflingParticipantState.CANCELLED);
        SHUFFLING_PARTICIPANT_TABLE.insert(this);
        LISTENER_MANAGER.notify(this, ShufflingParticipantEvent.PARTICIPANT_CANCELLED);
    }

    public byte[] getDataTransactionFullHash() {
        return dataTransactionFullHash;
    }

    void setProcessed(byte[] dataTransactionFullHash) {
        if (this.dataTransactionFullHash != null) {
            throw new IllegalStateException("dataTransactionFullHash already set");
        }
        setState(ShufflingParticipantState.PROCESSED);
        this.dataTransactionFullHash = dataTransactionFullHash;
        SHUFFLING_PARTICIPANT_TABLE.insert(this);
        LISTENER_MANAGER.notify(this, ShufflingParticipantEvent.PARTICIPANT_PROCESSED);
    }

    public ShufflingParticipant getPreviousParticipant() {
        if (index == 0) {
            return null;
        }
        return SHUFFLING_PARTICIPANT_TABLE.getBy(new H2ClauseLongClause("shuffling_id", shufflingId).and(new H2ClauseIntClause("participant_index", index - 1)));
    }

    void verify() {
        setState(ShufflingParticipantState.VERIFIED);
        SHUFFLING_PARTICIPANT_TABLE.insert(this);
        LISTENER_MANAGER.notify(this, ShufflingParticipantEvent.PARTICIPANT_VERIFIED);
    }

    void delete() {
        SHUFFLING_PARTICIPANT_TABLE.delete(this);
    }

    private final static class ShufflingData {

        private final long shufflingId;
        private final long accountId;
        private final H2Key h2Key;
        private final byte[][] data;
        private final int transactionTimestamp;
        private final int height;

        private ShufflingData(long shufflingId, long accountId, byte[][] data, int transactionTimestamp, int height) {
            this.shufflingId = shufflingId;
            this.accountId = accountId;
            this.h2Key = SHUFFLING_DATA_DB_KEY_FACTORY.newKey(shufflingId, accountId);
            this.data = data;
            this.transactionTimestamp = transactionTimestamp;
            this.height = height;
        }

        private ShufflingData(ResultSet rs, H2Key h2Key) throws SQLException {
            this.shufflingId = rs.getLong("shuffling_id");
            this.accountId = rs.getLong("account_id");
            this.h2Key = h2Key;
            this.data = H2Utils.h2getArray(rs, "data", byte[][].class, Convert.EC_EMPTY_BYTES);
            this.transactionTimestamp = rs.getInt("transaction_timestamp");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO shuffling_data (shuffling_id, account_id, data, "
                    + "transaction_timestamp, height) "
                    + "VALUES (?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, this.shufflingId);
                pstmt.setLong(++i, this.accountId);
                H2Utils.h2setArrayEmptyToNull(pstmt, ++i, this.data);
                pstmt.setInt(++i, this.transactionTimestamp);
                pstmt.setInt(++i, this.height);
                pstmt.executeUpdate();
            }
        }

    }

}
