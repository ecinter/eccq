package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public final class Phasing extends AbstractEnclosure {

    private static final String appendixName = "Phasing";

    private static final Fee PHASING_FEE = (transaction, appendage) -> {
        long fee = 0;
        com.inesv.ecchain.kernel.core.Phasing phasing = (com.inesv.ecchain.kernel.core.Phasing) appendage;
        if (!phasing.params.getVoteWeighting().isBalanceIndependent()) {
            fee += 20 * Constants.ONE_EC;
        } else {
            fee += Constants.ONE_EC;
        }
        if (phasing.hashedSecret.length > 0) {
            fee += (1 + (phasing.hashedSecret.length - 1) / 32) * Constants.ONE_EC;
        }
        fee += Constants.ONE_EC * phasing.linkedFullHashes.length;
        return fee;
    };
    private final int finishHeight;
    private final PhasingParams params;
    private final byte[][] linkedFullHashes;
    private final byte[] hashedSecret;
    private final byte algorithm;

    Phasing(ByteBuffer buffer, byte transactionVersion) {
        super(buffer, transactionVersion);
        finishHeight = buffer.getInt();
        params = new PhasingParams(buffer);

        byte linkedFullHashesSize = buffer.get();
        if (linkedFullHashesSize > 0) {
            linkedFullHashes = new byte[linkedFullHashesSize][];
            for (int i = 0; i < linkedFullHashesSize; i++) {
                linkedFullHashes[i] = new byte[32];
                buffer.get(linkedFullHashes[i]);
            }
        } else {
            linkedFullHashes = Convert.EC_EMPTY_BYTES;
        }
        byte hashedSecretLength = buffer.get();
        if (hashedSecretLength > 0) {
            hashedSecret = new byte[hashedSecretLength];
            buffer.get(hashedSecret);
        } else {
            hashedSecret = Convert.EC_EMPTY_BYTE;
        }
        algorithm = buffer.get();
    }

    Phasing(JSONObject attachmentData) {
        super(attachmentData);
        finishHeight = ((Long) attachmentData.get("phasingFinishHeight")).intValue();
        params = new PhasingParams(attachmentData);
        JSONArray linkedFullHashesJson = (JSONArray) attachmentData.get("phasingLinkedFullHashes");
        if (linkedFullHashesJson != null && linkedFullHashesJson.size() > 0) {
            linkedFullHashes = new byte[linkedFullHashesJson.size()][];
            for (int i = 0; i < linkedFullHashes.length; i++) {
                linkedFullHashes[i] = Convert.parseHexString((String) linkedFullHashesJson.get(i));
            }
        } else {
            linkedFullHashes = Convert.EC_EMPTY_BYTES;
        }
        String hashedSecret = Convert.emptyToNull((String) attachmentData.get("phasingHashedSecret"));
        if (hashedSecret != null) {
            this.hashedSecret = Convert.parseHexString(hashedSecret);
            this.algorithm = ((Long) attachmentData.get("phasingHashedSecretAlgorithm")).byteValue();
        } else {
            this.hashedSecret = Convert.EC_EMPTY_BYTE;
            this.algorithm = 0;
        }
    }

    public Phasing(int finishHeight, PhasingParams phasingParams, byte[][] linkedFullHashes, byte[] hashedSecret, byte algorithm) {
        this.finishHeight = finishHeight;
        this.params = phasingParams;
        this.linkedFullHashes = Convert.nullToEmpty(linkedFullHashes);
        this.hashedSecret = hashedSecret != null ? hashedSecret : Convert.EC_EMPTY_BYTE;
        this.algorithm = algorithm;
    }

    static com.inesv.ecchain.kernel.core.Phasing parse(JSONObject attachmentData) {
        if (!Enclosure.hasEnclosure(appendixName, attachmentData)) {
            return null;
        }
        return new com.inesv.ecchain.kernel.core.Phasing(attachmentData);
    }

    @Override
    String getAppendixName() {
        return appendixName;
    }

    @Override
    int getMySize() {
        return 4 + params.getMySize() + 1 + 32 * linkedFullHashes.length + 1 + hashedSecret.length + 1;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
        buffer.putInt(finishHeight);
        params.putMyBytes(buffer);
        buffer.put((byte) linkedFullHashes.length);
        for (byte[] hash : linkedFullHashes) {
            buffer.put(hash);
        }
        buffer.put((byte) hashedSecret.length);
        buffer.put(hashedSecret);
        buffer.put(algorithm);
    }

    @Override
    void putMyJSON(JSONObject json) {
        json.put("phasingFinishHeight", finishHeight);
        params.putMyJSON(json);
        if (linkedFullHashes.length > 0) {
            JSONArray linkedFullHashesJson = new JSONArray();
            for (byte[] hash : linkedFullHashes) {
                linkedFullHashesJson.add(Convert.toHexString(hash));
            }
            json.put("phasingLinkedFullHashes", linkedFullHashesJson);
        }
        if (hashedSecret.length > 0) {
            json.put("phasingHashedSecret", Convert.toHexString(hashedSecret));
            json.put("phasingHashedSecretAlgorithm", algorithm);
        }
    }

    @Override
    void validate(Transaction transaction) throws EcValidationException {
        params.validate();
        int currentHeight = EcBlockchainImpl.getInstance().getHeight();
        if (params.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.TRANSACTION) {
            if (linkedFullHashes.length == 0 || linkedFullHashes.length > Constants.EC_MAX_PHASING_LINKED_TRANSACTIONS) {
                throw new EcNotValidExceptionEc("Invalid number of linkedFullHashes " + linkedFullHashes.length);
            }
            Set<Long> linkedTransactionIds = new HashSet<>(linkedFullHashes.length);
            for (byte[] hash : linkedFullHashes) {
                if (Convert.emptyToNull(hash) == null || hash.length != 32) {
                    throw new EcNotValidExceptionEc("Invalid linkedFullHash " + Convert.toHexString(hash));
                }
                if (EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK) {
                    if (!linkedTransactionIds.add(Convert.fullhashtoid(hash))) {
                        throw new EcNotValidExceptionEc("Duplicate linked transaction ids");
                    }
                }
                TransactionImpl linkedTransaction = TransactionH2.selectTransactionByFullHash(hash, currentHeight);
                if (linkedTransaction != null) {
                    if (transaction.getTimestamp() - linkedTransaction.getTimestamp() > Constants.EC_MAX_REFERENCED_TRANSACTION_TIMESPAN) {
                        throw new EcNotValidExceptionEc("Linked transaction cannot be more than 60 days older than the phased transaction");
                    }
                    if (linkedTransaction.getPhasing() != null) {
                        throw new EcNotCurrentlyValidExceptionEc("Cannot link to an already existing phased transaction");
                    }
                }
            }
            if (params.getQuorum() > linkedFullHashes.length) {
                throw new EcNotValidExceptionEc("Quorum of " + params.getQuorum() + " cannot be achieved in by-transaction voting with "
                        + linkedFullHashes.length + " linked full hashes only");
            }
        } else {
            if (linkedFullHashes.length != 0) {
                throw new EcNotValidExceptionEc("LinkedFullHashes can only be used with VotingModel.TRANSACTION");
            }
        }

        if (params.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
            if (params.getQuorum() != 1) {
                throw new EcNotValidExceptionEc("Quorum must be 1 for by-hash voting");
            }
            if (hashedSecret.length == 0 || hashedSecret.length > Byte.MAX_VALUE) {
                throw new EcNotValidExceptionEc("Invalid hashedSecret " + Convert.toHexString(hashedSecret));
            }
            if (PhasingPoll.getHashFunction(algorithm) == null) {
                throw new EcNotValidExceptionEc("Invalid hashedSecretAlgorithm " + algorithm);
            }
        } else {
            if (hashedSecret.length != 0) {
                throw new EcNotValidExceptionEc("HashedSecret can only be used with VotingModel.HASH");
            }
            if (algorithm != 0) {
                throw new EcNotValidExceptionEc("HashedSecretAlgorithm can only be used with VotingModel.HASH");
            }
        }

        if (finishHeight <= currentHeight + (params.getVoteWeighting().acceptsVotes() ? 2 : 1)
                || finishHeight >= currentHeight + Constants.EC_MAX_PHASING_DURATION) {
            throw new EcNotCurrentlyValidExceptionEc("Invalid finish height " + finishHeight);
        }
    }

    @Override
    void validateAtFinish(Transaction transaction) throws EcValidationException {
        params.checkApprovable();
    }

    @Override
    void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
        PhasingPoll.addPoll(transaction, this);
    }

    @Override
    boolean isPhasable() {
        return false;
    }

    @Override
    public Fee getBaselineFee(Transaction transaction) {
        return PHASING_FEE;
    }

    private void release(TransactionImpl transaction) {
        Account senderAccount = Account.getAccount(transaction.getSenderId());
        Account recipientAccount = transaction.getRecipientId() == 0 ? null : Account.getAccount(transaction.getRecipientId());
        transaction.getAppendages().forEach(appendage -> {
            if (appendage.isPhasable()) {
                appendage.apply(transaction, senderAccount, recipientAccount);
            }
        });
        TransactionProcessorImpl.getInstance().notifyListeners(Collections.singletonList(transaction), TransactionProcessorEvent.RELEASE_PHASED_TRANSACTION);
        LoggerUtil.logDebug("Transaction " + transaction.getStringId() + " has been released");
    }

    void reject(TransactionImpl transaction) {
        Account senderAccount = Account.getAccount(transaction.getSenderId());
        transaction.getTransactionType().undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceNQT(LedgerEvent.REJECT_PHASED_TRANSACTION, transaction.getTransactionId(),
                transaction.getAmountNQT());
        TransactionProcessorImpl.getInstance()
                .notifyListeners(Collections.singletonList(transaction), TransactionProcessorEvent.REJECT_PHASED_TRANSACTION);
        LoggerUtil.logDebug("Transaction " + transaction.getStringId() + " has been rejected");
    }

    void countVotes(TransactionImpl transaction) {
        if (EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK && PhasingPoll.getResult(transaction.getTransactionId()) != null) {
            return;
        }
        PhasingPoll poll = PhasingPoll.getPoll(transaction.getTransactionId());
        long result = poll.countVotes();
        poll.finish(result);
        if (result >= poll.getQuorum()) {
            try {
                release(transaction);
            } catch (RuntimeException e) {
                LoggerUtil.logError("Failed to release phased transaction " + transaction.getJSONObject().toJSONString(), e);
                reject(transaction);
            }
        } else {
            reject(transaction);
        }
    }

    void tryCountVotes(TransactionImpl transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        PhasingPoll poll = PhasingPoll.getPoll(transaction.getTransactionId());
        long result = poll.countVotes();
        if (result >= poll.getQuorum()) {
            if (!transaction.attachmentIsDuplicate(duplicates, false)) {
                try {
                    release(transaction);
                    poll.finish(result);
                    LoggerUtil.logDebug("Early finish of transaction " + transaction.getStringId() + " at height " + EcBlockchainImpl.getInstance().getHeight());
                } catch (RuntimeException e) {
                    LoggerUtil.logError("Failed to release phased transaction " + transaction.getJSONObject().toJSONString(), e);
                }
            } else {
                LoggerUtil.logDebug("At height " + EcBlockchainImpl.getInstance().getHeight() + " phased transaction " + transaction.getStringId()
                        + " is duplicate, cannot finish early");
            }
        } else {
            LoggerUtil.logDebug("At height " + EcBlockchainImpl.getInstance().getHeight() + " phased transaction " + transaction.getStringId()
                    + " does not yet meet quorum, cannot finish early");
        }
    }

    public int getFinishHeight() {
        return finishHeight;
    }

    public long getQuorum() {
        return params.getQuorum();
    }

    public long[] getWhitelist() {
        return params.getWhitelist();
    }

    public VoteWeighting getVoteWeighting() {
        return params.getVoteWeighting();
    }

    public byte[][] getLinkedFullHashes() {
        return linkedFullHashes;
    }

    public byte[] getHashedSecret() {
        return hashedSecret;
    }

    public byte getAlgorithm() {
        return algorithm;
    }

    public PhasingParams getParams() {
        return params;
    }
}
