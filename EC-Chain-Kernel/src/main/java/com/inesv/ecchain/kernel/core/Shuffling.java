package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.EcAnonymouslyEncrypted;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.H2.*;

import javax.annotation.PostConstruct;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class Shuffling {

    private static final ListenerManager<Shuffling, ShufflingEvent> LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLongKeyFactory<Shuffling> SHUFFLING_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Shuffling>("Id") {

        @Override
        public H2Key newKey(Shuffling shuffling) {
            return shuffling.h2Key;
        }

    };
    private static final VersionedEntityH2Table<Shuffling> SHUFFLING_TABLE = new VersionedEntityH2Table<Shuffling>("shuffling", SHUFFLING_DB_KEY_FACTORY) {

        @Override
        protected Shuffling load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Shuffling(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Shuffling shuffling) throws SQLException {
            shuffling.saveShuffling(con);
        }

    };

    @PostConstruct
    public static void initPostConstruct() {
        EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
            if (block.getHeight() < Constants.EC_SHUFFLING_BLOCK || block.getTransactions().size() == Constants.EC_MAX_NUMBER_OF_TRANSACTIONS
                    || block.getPayloadLength() > Constants.EC_MAX_PAYLOAD_LENGTH - Constants.EC_MIN_TRANSACTION_SIZE) {
                return;
            }
            List<Shuffling> shufflings = new ArrayList<>();
            try (H2Iterator<Shuffling> iterator = getActiveShufflings(0, -1)) {
                for (Shuffling shuffling : iterator) {
                    if (!shuffling.isFull(block)) {
                        shufflings.add(shuffling);
                    }
                }
            }
            shufflings.forEach(shuffling -> {
                if (--shuffling.blocksRemaining <= 0) {
                    shuffling.cancel(block);
                } else {
                    SHUFFLING_TABLE.insert(shuffling);
                }
            });
        }, EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);
    }

    private final long id;
    private final H2Key h2Key;
    private final long holdingId;
    private final HoldingType holdingType;
    private final long issuerId;
    private final long amount;
    private final byte participantCount;
    private short blocksRemaining;
    private byte registrantCount;
    private Stage stage;
    private long assigneeAccountId;
    private byte[][] recipientPublicKeys;

    private Shuffling(Transaction transaction, Mortgaged.ShufflingCreation attachment) {
        this.id = transaction.getTransactionId();
        this.h2Key = SHUFFLING_DB_KEY_FACTORY.newKey(this.id);
        this.holdingId = attachment.getHoldingId();
        this.holdingType = attachment.getHoldingType();
        this.issuerId = transaction.getSenderId();
        this.amount = attachment.getAmount();
        this.participantCount = attachment.getParticipantCount();
        this.blocksRemaining = attachment.getRegistrationPeriod();
        this.stage = Stage.REGISTRATION;
        this.assigneeAccountId = issuerId;
        this.recipientPublicKeys = Convert.EC_EMPTY_BYTES;
        this.registrantCount = 1;
    }

    private Shuffling(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.holdingId = rs.getLong("holding_id");
        this.holdingType = HoldingType.get(rs.getByte("holding_type"));
        this.issuerId = rs.getLong("issuer_id");
        this.amount = rs.getLong("amount");
        this.participantCount = rs.getByte("participant_count");
        this.blocksRemaining = rs.getShort("blocks_remaining");
        this.stage = Stage.get(rs.getByte("stage"));
        this.assigneeAccountId = rs.getLong("assignee_account_id");
        this.recipientPublicKeys = H2Utils.h2getArray(rs, "recipient_public_keys", byte[][].class, Convert.EC_EMPTY_BYTES);
        this.registrantCount = rs.getByte("registrant_count");
    }

    public static boolean addShufflingListener(Listener<Shuffling> listener, ShufflingEvent eventType) {
        return LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static int getCount() {
        return SHUFFLING_TABLE.getCount();
    }

    public static int getActiveCount() {
        return SHUFFLING_TABLE.getCount(new H2ClauseNotNullClause("blocks_remaining"));
    }

    public static H2Iterator<Shuffling> getAllShuffling(int from, int to) {
        return SHUFFLING_TABLE.getAll(from, to, " ORDER BY blocks_remaining NULLS LAST, height DESC ");
    }

    public static H2Iterator<Shuffling> getActiveShufflings(int from, int to) {
        return SHUFFLING_TABLE.getManyBy(new H2ClauseNotNullClause("blocks_remaining"), from, to, " ORDER BY blocks_remaining, height DESC ");
    }

    public static H2Iterator<Shuffling> getFinishedShufflings(int from, int to) {
        return SHUFFLING_TABLE.getManyBy(new H2ClauseNullClause("blocks_remaining"), from, to, " ORDER BY height DESC ");
    }

    public static Shuffling getShuffling(long shufflingId) {
        return SHUFFLING_TABLE.get(SHUFFLING_DB_KEY_FACTORY.newKey(shufflingId));
    }

    public static Shuffling getShuffling(byte[] fullHash) {
        long shufflingId = Convert.fullhashtoid(fullHash);
        Shuffling shuffling = SHUFFLING_TABLE.get(SHUFFLING_DB_KEY_FACTORY.newKey(shufflingId));
        if (shuffling != null && !Arrays.equals(shuffling.getFullHash(), fullHash)) {
            LoggerUtil.logInfo("Shuffling with different hash " + Convert.toHexString(shuffling.getFullHash()) + " but same Id found for hash " + Convert.toHexString(fullHash));
            return null;
        }
        return shuffling;
    }

    public static int getHoldingShufflingCount(long holdingId, boolean includeFinished) {
        H2Clause clause = holdingId != 0 ? new H2ClauseLongClause("holding_id", holdingId) : new H2ClauseNullClause("holding_id");
        if (!includeFinished) {
            clause = clause.and(new H2ClauseNotNullClause("blocks_remaining"));
        }
        return SHUFFLING_TABLE.getCount(clause);
    }

    public static H2Iterator<Shuffling> getHoldingShufflings(long holdingId, Stage stage, boolean includeFinished, int from, int to) {
        H2Clause clause = holdingId != 0 ? new H2ClauseLongClause("holding_id", holdingId) : new H2ClauseNullClause("holding_id");
        if (!includeFinished) {
            clause = clause.and(new H2ClauseNotNullClause("blocks_remaining"));
        }
        if (stage != null) {
            clause = clause.and(new H2ClauseByteClause("stage", stage.getCode()));
        }
        return SHUFFLING_TABLE.getManyBy(clause, from, to, " ORDER BY blocks_remaining NULLS LAST, height DESC ");
    }

    public static H2Iterator<Shuffling> getAccountShufflings(long accountId, boolean includeFinished, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT shuffling.* FROM shuffling, shuffling_participant WHERE "
                    + "shuffling_participant.account_id = ? AND shuffling.id = shuffling_participant.shuffling_id "
                    + (includeFinished ? "" : "AND shuffling.blocks_remaining IS NOT NULL ")
                    + "AND shuffling.latest = TRUE AND shuffling_participant.latest = TRUE ORDER BY blocks_remaining NULLS LAST, height DESC "
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return SHUFFLING_TABLE.getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<Shuffling> getAssignedShufflings(long assigneeAccountId, int from, int to) {
        return SHUFFLING_TABLE.getManyBy(new H2ClauseLongClause("assignee_account_id", assigneeAccountId)
                        .and(new H2ClauseByteClause("stage", Stage.PROCESSING.getCode())), from, to,
                " ORDER BY blocks_remaining NULLS LAST, height DESC ");
    }

    static void addShuffling(Transaction transaction, Mortgaged.ShufflingCreation attachment) {
        Shuffling shuffling = new Shuffling(transaction, attachment);
        SHUFFLING_TABLE.insert(shuffling);
        ShufflingParticipant.addParticipant(shuffling.getId(), transaction.getSenderId(), 0);
        LISTENER_MANAGER.notify(shuffling, ShufflingEvent.SHUFFLING_CREATED);
    }

    public static void start() {
    }

    private static byte[] getParticipantsHash(Iterable<ShufflingParticipant> participants) {
        MessageDigest digest = Crypto.sha256();
        participants.forEach(participant -> digest.update(Convert.toBytes(participant.getAccountId())));
        return digest.digest();
    }

    private void saveShuffling(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO shuffling (id, holding_id, holding_type, "
                + "issuer_id, amount, participant_count, blocks_remaining, stage, assignee_account_id, "
                + "recipient_public_keys, registrant_count, height, latest) "
                + "KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            H2Utils.h2setLongZeroToNull(pstmt, ++i, this.holdingId);
            pstmt.setByte(++i, this.holdingType.getCode());
            pstmt.setLong(++i, this.issuerId);
            pstmt.setLong(++i, this.amount);
            pstmt.setByte(++i, this.participantCount);
            H2Utils.h2setShortZeroToNull(pstmt, ++i, this.blocksRemaining);
            pstmt.setByte(++i, this.getStage().getCode());
            H2Utils.h2setLongZeroToNull(pstmt, ++i, this.assigneeAccountId);
            H2Utils.h2setArrayEmptyToNull(pstmt, ++i, this.recipientPublicKeys);
            pstmt.setByte(++i, this.registrantCount);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getHoldingId() {
        return holdingId;
    }

    public HoldingType getHoldingType() {
        return holdingType;
    }

    public long getIssuerId() {
        return issuerId;
    }

    public long getAmount() {
        return amount;
    }

    public byte getParticipantCount() {
        return participantCount;
    }

    public byte getRegistrantCount() {
        return registrantCount;
    }

    public short getBlocksRemaining() {
        return blocksRemaining;
    }

    public Stage getStage() {
        return stage;
    }

    private void setStage(Stage stage, long assigneeAccountId, short blocksRemaining) {
        if (!this.stage.canBecome(stage)) {
            throw new IllegalStateException(String.format("Shuffling in stage %s cannot go to stage %s", this.stage, stage));
        }
        if ((stage == Stage.VERIFICATION || stage == Stage.DONE) && assigneeAccountId != 0) {
            throw new IllegalArgumentException(String.format("Invalid assigneeAccountId %s for stage %s", Long.toUnsignedString(assigneeAccountId), stage));
        }
        if ((stage == Stage.REGISTRATION || stage == Stage.PROCESSING || stage == Stage.BLAME) && assigneeAccountId == 0) {
            throw new IllegalArgumentException(String.format("In stage %s assigneeAccountId cannot be 0", stage));
        }
        if ((stage == Stage.DONE || stage == Stage.CANCELLED) && blocksRemaining != 0) {
            throw new IllegalArgumentException(String.format("For stage %s remaining blocks cannot be %s", stage, blocksRemaining));
        }
        this.stage = stage;
        this.assigneeAccountId = assigneeAccountId;
        this.blocksRemaining = blocksRemaining;
        LoggerUtil.logInfo("Shuffling " + Long.toUnsignedString(id) + " entered stage " + this.stage + ", assignee " + Long.toUnsignedString(this.assigneeAccountId) + ", remaining blocks " + this.blocksRemaining);
    }

    public long getAssigneeAccountId() {
        return assigneeAccountId;
    }

    public byte[][] getRecipientPublicKeys() {
        return recipientPublicKeys;
    }

    public ShufflingParticipant getParticipant(long accountId) {
        return ShufflingParticipant.getParticipant(id, accountId);
    }

    public ShufflingParticipant getLastParticipant() {
        return ShufflingParticipant.getLastParticipant(id);
    }

    public byte[] getStateHash() {
        return stage.getHash(this);
    }

    public byte[] getFullHash() {
        return TransactionH2.getFullHash(id);
    }

    public Mortgaged.ShufflingMortgaged process(final long accountId, final String secretPhrase, final byte[] recipientPublicKey) {
        byte[][] data = Convert.EC_EMPTY_BYTES;
        byte[] shufflingStateHash = null;
        int participantIndex = 0;
        List<ShufflingParticipant> shufflingParticipants = new ArrayList<>();
        EcBlockchainImpl.getInstance().readECLock();
        // Read the participant list for the shuffling
        try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(id)) {
            for (ShufflingParticipant participant : participants) {
                shufflingParticipants.add(participant);
                if (participant.getNextAccountId() == accountId) {
                    data = participant.getData();
                    shufflingStateHash = participant.getDataTransactionFullHash();
                    participantIndex = shufflingParticipants.size();
                }
            }
            if (shufflingStateHash == null) {
                shufflingStateHash = getParticipantsHash(shufflingParticipants);
            }
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        boolean isLast = participantIndex == participantCount - 1;
        // decrypt the tokens bundled in the current data
        List<byte[]> outputDataList = new ArrayList<>();
        for (byte[] bytes : data) {
            EcAnonymouslyEncrypted encryptedData = EcAnonymouslyEncrypted.readEncryptedData(bytes);
            try {
                byte[] decrypted = encryptedData.decrypt(secretPhrase);
                outputDataList.add(decrypted);
            } catch (Exception e) {
                LoggerUtil.logError("Decryption failed", e);
                return isLast ? new Mortgaged.ShufflingRecipients(this.id, Convert.EC_EMPTY_BYTES, shufflingStateHash)
                        : new Mortgaged.ShufflingProcessing(this.id, Convert.EC_EMPTY_BYTES, shufflingStateHash);
            }
        }
        // Calculate the token for the current sender by iteratively encrypting it using the public key of all the participants
        // which did not perform shuffle processing yet
        byte[] bytesToEncrypt = recipientPublicKey;
        byte[] nonce = Convert.toBytes(this.id);
        for (int i = shufflingParticipants.size() - 1; i > participantIndex; i--) {
            ShufflingParticipant participant = shufflingParticipants.get(i);
            byte[] participantPublicKey = Account.getPublicKey(participant.getAccountId());
            EcAnonymouslyEncrypted encryptedData = EcAnonymouslyEncrypted.encrypt(bytesToEncrypt, secretPhrase, participantPublicKey, nonce);
            bytesToEncrypt = encryptedData.getBytes();
        }
        outputDataList.add(bytesToEncrypt);
        // Shuffle the tokens and saveShuffling the shuffled tokens as the participant data
        Collections.sort(outputDataList, Convert.byteArrayComparator);
        if (isLast) {
            Set<Long> recipientAccounts = new HashSet<>(participantCount);
            for (byte[] publicKey : outputDataList) {
                if (!Crypto.isCanonicalPublicKey(publicKey) || !recipientAccounts.add(Account.getId(publicKey))) {
                    // duplicate or invalid recipient public key
                    LoggerUtil.logDebug("Invalid recipient public key " + Convert.toHexString(publicKey));
                    return new Mortgaged.ShufflingRecipients(this.id, Convert.EC_EMPTY_BYTES, shufflingStateHash);
                }
            }
            // last participant prepares ShufflingRecipients transaction instead of ShufflingProcessing
            return new Mortgaged.ShufflingRecipients(this.id, outputDataList.toArray(new byte[outputDataList.size()][]),
                    shufflingStateHash);
        } else {
            byte[] previous = null;
            for (byte[] decrypted : outputDataList) {
                if (previous != null && Arrays.equals(decrypted, previous)) {
                    LoggerUtil.logDebug("Duplicate decrypted data");
                    return new Mortgaged.ShufflingProcessing(this.id, Convert.EC_EMPTY_BYTES, shufflingStateHash);
                }
                if (decrypted.length != 32 + 64 * (participantCount - participantIndex - 1)) {
                    LoggerUtil.logDebug("Invalid encrypted data length in process " + decrypted.length);
                    return new Mortgaged.ShufflingProcessing(this.id, Convert.EC_EMPTY_BYTES, shufflingStateHash);
                }
                previous = decrypted;
            }
            return new Mortgaged.ShufflingProcessing(this.id, outputDataList.toArray(new byte[outputDataList.size()][]),
                    shufflingStateHash);
        }
    }

    public Mortgaged.ShufflingCancellation revealKeySeeds(final String secretPhrase, long cancellingAccountId, byte[] shufflingStateHash) {
        EcBlockchainImpl.getInstance().readECLock();
        try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(id)) {
            if (cancellingAccountId != this.assigneeAccountId) {
                throw new RuntimeException(String.format("Current shuffling cancellingAccountId %s does not match %s",
                        Long.toUnsignedString(this.assigneeAccountId), Long.toUnsignedString(cancellingAccountId)));
            }
            if (shufflingStateHash == null || !Arrays.equals(shufflingStateHash, getStateHash())) {
                throw new RuntimeException("Current shuffling state hash does not match");
            }
            long accountId = Account.getId(Crypto.getPublicKey(secretPhrase));
            byte[][] data = null;
            while (participants.hasNext()) {
                ShufflingParticipant participant = participants.next();
                if (participant.getAccountId() == accountId) {
                    data = participant.getData();
                    break;
                }
            }
            if (!participants.hasNext()) {
                throw new RuntimeException("Last participant cannot have keySeeds to reveal");
            }
            if (data == null) {
                throw new RuntimeException("Account " + Long.toUnsignedString(accountId) + " has not submitted data");
            }
            final byte[] nonce = Convert.toBytes(this.id);
            final List<byte[]> keySeeds = new ArrayList<>();
            byte[] nextParticipantPublicKey = Account.getPublicKey(participants.next().getAccountId());
            byte[] keySeed = Crypto.getEcKeySeed(secretPhrase, nextParticipantPublicKey, nonce);
            keySeeds.add(keySeed);
            byte[] publicKey = Crypto.getPublicKey(keySeed);
            byte[] decryptedBytes = null;
            // find the data that we encrypted
            for (byte[] bytes : data) {
                EcAnonymouslyEncrypted encryptedData = EcAnonymouslyEncrypted.readEncryptedData(bytes);
                if (Arrays.equals(encryptedData.getPublicKey(), publicKey)) {
                    try {
                        decryptedBytes = encryptedData.decrypt(keySeed, nextParticipantPublicKey);
                        break;
                    } catch (Exception ignore) {
                    }
                }
            }
            if (decryptedBytes == null) {
                throw new RuntimeException("None of the encrypted data could be decrypted");
            }
            // decrypt all iteratively, adding the key seeds to the result
            while (participants.hasNext()) {
                nextParticipantPublicKey = Account.getPublicKey(participants.next().getAccountId());
                keySeed = Crypto.getEcKeySeed(secretPhrase, nextParticipantPublicKey, nonce);
                keySeeds.add(keySeed);
                EcAnonymouslyEncrypted encryptedData = EcAnonymouslyEncrypted.readEncryptedData(decryptedBytes);
                decryptedBytes = encryptedData.decrypt(keySeed, nextParticipantPublicKey);
            }
            return new Mortgaged.ShufflingCancellation(this.id, data, keySeeds.toArray(new byte[keySeeds.size()][]),
                    shufflingStateHash, cancellingAccountId);
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
    }

    void addParticipant(long participantId) {
        // Update the shuffling assignee to point to the new participant and update the next pointer of the existing participant
        // to the new participant
        ShufflingParticipant lastParticipant = ShufflingParticipant.getParticipant(this.id, this.assigneeAccountId);
        lastParticipant.setNextAccountId(participantId);
        ShufflingParticipant.addParticipant(this.id, participantId, this.registrantCount);
        this.registrantCount += 1;
        // Check if participant registration is complete and if so update the shuffling
        if (this.registrantCount == this.participantCount) {
            setStage(Stage.PROCESSING, this.issuerId, Constants.EC_SHUFFLING_PROCESSING_DEADLINE);
        } else {
            this.assigneeAccountId = participantId;
        }
        SHUFFLING_TABLE.insert(this);
        if (stage == Stage.PROCESSING) {
            LISTENER_MANAGER.notify(this, ShufflingEvent.SHUFFLING_PROCESSING_ASSIGNED);
        }
    }

    void updateParticipantData(Transaction transaction, Mortgaged.ShufflingProcessing attachment) {
        long participantId = transaction.getSenderId();
        byte[][] data = attachment.getData();
        ShufflingParticipant participant = ShufflingParticipant.getParticipant(this.id, participantId);
        participant.setData(data, transaction.getTimestamp());
        participant.setProcessed(((TransactionImpl) transaction).fullHash());
        if (data != null && data.length == 0) {
            // couldn't decrypt all data from previous participants
            cancelBy(participant);
            return;
        }
        this.assigneeAccountId = participant.getNextAccountId();
        this.blocksRemaining = Constants.EC_SHUFFLING_PROCESSING_DEADLINE;
        SHUFFLING_TABLE.insert(this);
        LISTENER_MANAGER.notify(this, ShufflingEvent.SHUFFLING_PROCESSING_ASSIGNED);
    }

    void updateRecipients(Transaction transaction, Mortgaged.ShufflingRecipients attachment) {
        long participantId = transaction.getSenderId();
        this.recipientPublicKeys = attachment.getRecipientPublicKeys();
        ShufflingParticipant participant = ShufflingParticipant.getParticipant(this.id, participantId);
        participant.setProcessed(((TransactionImpl) transaction).fullHash());
        if (recipientPublicKeys.length == 0) {
            // couldn't decrypt all data from previous participants
            cancelBy(participant);
            return;
        }
        participant.verify();
        // last participant announces all valid recipient public keys
        for (byte[] recipientPublicKey : recipientPublicKeys) {
            long recipientId = Account.getId(recipientPublicKey);
            if (Account.setOrVerify(recipientId, recipientPublicKey)) {
                Account.addOrGetAccount(recipientId).apply(recipientPublicKey);
            }
        }
        setStage(Stage.VERIFICATION, 0, (short) (Constants.EC_SHUFFLING_PROCESSING_DEADLINE + participantCount));
        SHUFFLING_TABLE.insert(this);
        LISTENER_MANAGER.notify(this, ShufflingEvent.SHUFFLING_PROCESSING_FINISHED);
    }

    void verify(long accountId) {
        ShufflingParticipant.getParticipant(id, accountId).verify();
        if (ShufflingParticipant.getVerifiedCount(id) == participantCount) {
            distribute();
        }
    }

    void cancelBy(ShufflingParticipant participant, byte[][] blameData, byte[][] keySeeds) {
        participant.cancel(blameData, keySeeds);
        boolean startingBlame = this.stage != Stage.BLAME;
        if (startingBlame) {
            setStage(Stage.BLAME, participant.getAccountId(), (short) (Constants.EC_SHUFFLING_PROCESSING_DEADLINE + participantCount));
        }
        SHUFFLING_TABLE.insert(this);
        if (startingBlame) {
            LISTENER_MANAGER.notify(this, ShufflingEvent.SHUFFLING_BLAME_STARTED);
        }
    }

    private void cancelBy(ShufflingParticipant participant) {
        cancelBy(participant, Convert.EC_EMPTY_BYTES, Convert.EC_EMPTY_BYTES);
    }

    private void distribute() {
        if (recipientPublicKeys.length != participantCount) {
            cancelBy(getLastParticipant());
            return;
        }
        for (byte[] recipientPublicKey : recipientPublicKeys) {
            byte[] publicKey = Account.getPublicKey(Account.getId(recipientPublicKey));
            if (publicKey != null && !Arrays.equals(publicKey, recipientPublicKey)) {
                // distribution not possible, do a cancellation on behalf of last participant instead
                cancelBy(getLastParticipant());
                return;
            }
        }
        LedgerEvent event = LedgerEvent.SHUFFLING_DISTRIBUTION;
        try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(id)) {
            for (ShufflingParticipant participant : participants) {
                Account participantAccount = Account.getAccount(participant.getAccountId());
                holdingType.addToBalance(participantAccount, event, this.id, this.holdingId, -amount);
                if (holdingType != HoldingType.EC) {
                    participantAccount.addToBalanceNQT(event, this.id, -Constants.EC_SHUFFLING_DEPOSIT_NQT);
                }
            }
        }
        for (byte[] recipientPublicKey : recipientPublicKeys) {
            long recipientId = Account.getId(recipientPublicKey);
            Account recipientAccount = Account.addOrGetAccount(recipientId);
            recipientAccount.apply(recipientPublicKey);
            holdingType.addToBalanceAndUnconfirmedBalance(recipientAccount, event, this.id, this.holdingId, amount);
            if (holdingType != HoldingType.EC) {
                recipientAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, Constants.EC_SHUFFLING_DEPOSIT_NQT);
            }
        }
        setStage(Stage.DONE, 0, (short) 0);
        SHUFFLING_TABLE.insert(this);
        LISTENER_MANAGER.notify(this, ShufflingEvent.SHUFFLING_DONE);
        if (Constants.DELETE_FINISHED) {
            delete();
        }
        LoggerUtil.logInfo("Shuffling " + Long.toUnsignedString(id) + " was distributed");
    }

    private void cancel(EcBlock ecBlock) {
        LedgerEvent event = LedgerEvent.SHUFFLING_CANCELLATION;
        long blamedAccountId = blame();
        try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(id)) {
            for (ShufflingParticipant participant : participants) {
                Account participantAccount = Account.getAccount(participant.getAccountId());
                holdingType.addToUnconfirmedBalance(participantAccount, event, this.id, this.holdingId, this.amount);
                if (participantAccount.getId() != blamedAccountId) {
                    if (holdingType != HoldingType.EC) {
                        participantAccount.addToUnconfirmedBalanceNQT(event, this.id, Constants.EC_SHUFFLING_DEPOSIT_NQT);
                    }
                } else {
                    if (holdingType == HoldingType.EC) {
                        participantAccount.addToUnconfirmedBalanceNQT(event, this.id, -Constants.EC_SHUFFLING_DEPOSIT_NQT);
                    }
                    participantAccount.addToBalanceNQT(event, this.id, -Constants.EC_SHUFFLING_DEPOSIT_NQT);
                }
            }
        }
        if (blamedAccountId != 0) {
            // as a penalty the deposit goes to the generators of the finish ecBlock and previous 3 blocks
            long fee = Constants.EC_SHUFFLING_DEPOSIT_NQT / 4;
            for (int i = 0; i < 3; i++) {
                Account previousGeneratorAccount = Account.getAccount(EcBlockH2.findBlockAtHeight(ecBlock.getHeight() - i - 1).getFoundryId());
                previousGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, ecBlock.getECId(), fee);
                previousGeneratorAccount.addToForgedBalanceNQT(fee);
                LoggerUtil.logInfo("Shuffling penalty " + ((double) fee) / Constants.ONE_EC + " EC awarded to forger at height " + (ecBlock.getHeight() - i - 1));
            }
            fee = Constants.EC_SHUFFLING_DEPOSIT_NQT - 3 * fee;
            Account blockGeneratorAccount = Account.getAccount(ecBlock.getFoundryId());
            blockGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, ecBlock.getECId(), fee);
            blockGeneratorAccount.addToForgedBalanceNQT(fee);
            LoggerUtil.logInfo("Shuffling penalty " + ((double) fee) / Constants.ONE_EC + " EC awarded to forger at height " + ecBlock.getHeight());
        }
        setStage(Stage.CANCELLED, blamedAccountId, (short) 0);
        SHUFFLING_TABLE.insert(this);
        LISTENER_MANAGER.notify(this, ShufflingEvent.SHUFFLING_CANCELLED);
        if (Constants.DELETE_FINISHED) {
            delete();
        }
        LoggerUtil.logInfo("Shuffling " + Long.toUnsignedString(id) + " was cancelled, blaming account " + Long.toUnsignedString(blamedAccountId));
    }

    private long blame() {
        // if registration never completed, no one is to blame
        if (stage == Stage.REGISTRATION) {
            LoggerUtil.logInfo("Registration never completed for shuffling " + Long.toUnsignedString(id));
            return 0;
        }
        // if no one submitted cancellation, blame the first one that did not submit processing data
        if (stage == Stage.PROCESSING) {
            LoggerUtil.logInfo("Participant " + Long.toUnsignedString(assigneeAccountId) + " did not submit processing");
            return assigneeAccountId;
        }
        List<ShufflingParticipant> participants = new ArrayList<>();
        try (H2Iterator<ShufflingParticipant> iterator = ShufflingParticipant.getParticipants(this.id)) {
            while (iterator.hasNext()) {
                participants.add(iterator.next());
            }
        }
        if (stage == Stage.VERIFICATION) {
            // if verification started, blame the first one who did not submit verification
            for (ShufflingParticipant participant : participants) {
                if (participant.getState() != ShufflingParticipantState.VERIFIED) {
                    LoggerUtil.logInfo("Participant " + Long.toUnsignedString(participant.getAccountId()) + " did not submit verification");
                    return participant.getAccountId();
                }
            }
            throw new RuntimeException("All participants submitted data and verifications, blame phase should not have been entered");
        }
        Set<Long> recipientAccounts = new HashSet<>(participantCount);
        // start from issuer and ecVerify all data up, skipping last participant
        for (int i = 0; i < participantCount - 1; i++) {
            ShufflingParticipant participant = participants.get(i);
            byte[][] keySeeds = participant.getKeySeeds();
            // if participant couldn't submit key seeds because he also couldn't decrypt some of the previous data, this should have been caught before
            if (keySeeds.length == 0) {
                LoggerUtil.logDebug("Participant " + Long.toUnsignedString(participant.getAccountId()) + " did not reveal keys");
                return participant.getAccountId();
            }
            byte[] publicKey = Crypto.getPublicKey(keySeeds[0]);
            EcAnonymouslyEncrypted encryptedData = null;
            for (byte[] bytes : participant.getBlameData()) {
                encryptedData = EcAnonymouslyEncrypted.readEncryptedData(bytes);
                if (Arrays.equals(publicKey, encryptedData.getPublicKey())) {
                    // found the data that this participant encrypted
                    break;
                }
            }
            if (encryptedData == null || !Arrays.equals(publicKey, encryptedData.getPublicKey())) {
                // participant lied about key seeds or data
                LoggerUtil.logInfo("Participant " + Long.toUnsignedString(participant.getAccountId()) + " did not submit blame data, or revealed invalid keys");
                return participant.getAccountId();
            }
            for (int k = i + 1; k < participantCount; k++) {
                ShufflingParticipant nextParticipant = participants.get(k);
                byte[] nextParticipantPublicKey = Account.getPublicKey(nextParticipant.getAccountId());
                byte[] keySeed = keySeeds[k - i - 1];
                byte[] participantBytes;
                try {
                    participantBytes = encryptedData.decrypt(keySeed, nextParticipantPublicKey);
                } catch (Exception e) {
                    // the next participant couldn't decrypt the data either, blame this one
                    LoggerUtil.logDebug("Could not decrypt data from participant " + Long.toUnsignedString(participant.getAccountId()));
                    return participant.getAccountId();
                }
                boolean isLast = k == participantCount - 1;
                if (isLast) {
                    // not encrypted data but plaintext recipient public key
                    if (!Crypto.isCanonicalPublicKey(publicKey)) {
                        // not a valid public key
                        LoggerUtil.logDebug("Participant " + Long.toUnsignedString(participant.getAccountId()) + " submitted invalid recipient public key");
                        return participant.getAccountId();
                    }
                    // check for collisions and assume they are intentional
                    byte[] currentPublicKey = Account.getPublicKey(Account.getId(participantBytes));
                    if (currentPublicKey != null && !Arrays.equals(currentPublicKey, participantBytes)) {
                        LoggerUtil.logDebug("Participant " + Long.toUnsignedString(participant.getAccountId()) + " submitted colliding recipient public key");
                        return participant.getAccountId();
                    }
                    if (!recipientAccounts.add(Account.getId(participantBytes))) {
                        LoggerUtil.logDebug("Participant " + Long.toUnsignedString(participant.getAccountId()) + " submitted duplicate recipient public key");
                        return participant.getAccountId();
                    }
                }
                if (nextParticipant.getState() == ShufflingParticipantState.CANCELLED && nextParticipant.getBlameData().length == 0) {
                    break;
                }
                boolean found = false;
                for (byte[] bytes : isLast ? recipientPublicKeys : nextParticipant.getBlameData()) {
                    if (Arrays.equals(participantBytes, bytes)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // the next participant did not include this participant's data
                    LoggerUtil.logDebug("Participant " + Long.toUnsignedString(nextParticipant.getAccountId()) + " did not include previous data");
                    return nextParticipant.getAccountId();
                }
                if (!isLast) {
                    encryptedData = EcAnonymouslyEncrypted.readEncryptedData(participantBytes);
                }
            }
        }
        return assigneeAccountId;
    }

    private void delete() {
        try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(id)) {
            for (ShufflingParticipant participant : participants) {
                participant.delete();
            }
        }
        SHUFFLING_TABLE.delete(this);
    }

    private boolean isFull(EcBlock ecBlock) {
        int transactionSize = Constants.EC_MIN_TRANSACTION_SIZE; // min transaction size with no attachment
        if (stage == Stage.REGISTRATION) {
            transactionSize += 1 + 32;
        } else { // must use same for PROCESSING/VERIFICATION/BLAME
            transactionSize = 16384; // max observed was 15647 for 30 participants
        }
        return ecBlock.getPayloadLength() + transactionSize > Constants.EC_MAX_PAYLOAD_LENGTH;
    }

    public enum Stage {
        REGISTRATION((byte) 0, new byte[]{1, 4}) {
            @Override
            byte[] getHash(Shuffling shuffling) {
                return shuffling.getFullHash();
            }
        },
        PROCESSING((byte) 1, new byte[]{2, 3, 4}) {
            @Override
            byte[] getHash(Shuffling shuffling) {
                if (shuffling.assigneeAccountId == shuffling.issuerId) {
                    try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(shuffling.id)) {
                        return getParticipantsHash(participants);
                    }
                } else {
                    ShufflingParticipant participant = shuffling.getParticipant(shuffling.assigneeAccountId);
                    return participant.getPreviousParticipant().getDataTransactionFullHash();
                }

            }
        },
        VERIFICATION((byte) 2, new byte[]{3, 4, 5}) {
            @Override
            byte[] getHash(Shuffling shuffling) {
                return shuffling.getLastParticipant().getDataTransactionFullHash();
            }
        },
        BLAME((byte) 3, new byte[]{4}) {
            @Override
            byte[] getHash(Shuffling shuffling) {
                return shuffling.getParticipant(shuffling.assigneeAccountId).getDataTransactionFullHash();
            }
        },
        CANCELLED((byte) 4, new byte[]{}) {
            @Override
            byte[] getHash(Shuffling shuffling) {
                byte[] hash = shuffling.getLastParticipant().getDataTransactionFullHash();
                if (hash != null && hash.length > 0) {
                    return hash;
                }
                try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(shuffling.id)) {
                    return getParticipantsHash(participants);
                }
            }
        },
        DONE((byte) 5, new byte[]{}) {
            @Override
            byte[] getHash(Shuffling shuffling) {
                return shuffling.getLastParticipant().getDataTransactionFullHash();
            }
        };

        private final byte code;
        private final byte[] allowedNext;

        Stage(byte code, byte[] allowedNext) {
            this.code = code;
            this.allowedNext = allowedNext;
        }

        public static Stage get(byte code) {
            for (Stage stage : Stage.values()) {
                if (stage.code == code) {
                    return stage;
                }
            }
            throw new IllegalArgumentException("No matching stage for " + code);
        }

        public byte getCode() {
            return code;
        }

        public boolean canBecome(Stage nextStage) {
            return Arrays.binarySearch(allowedNext, nextStage.code) >= 0;
        }

        abstract byte[] getHash(Shuffling shuffling);

    }

}
