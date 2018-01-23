package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.H2.H2Iterator;

import java.util.*;

public final class Shuffler {

    private static final Map<String, Map<Long, Shuffler>> SHUFFLINGS_MAP = new HashMap<>();
    private static final Map<Integer, Set<String>> EXPIRATIONS = new HashMap<>();

    static {

        Shuffling.addShufflingListener(shuffling -> {
            Map<Long, Shuffler> shufflerMap = getShufflers(shuffling);
            if (shufflerMap != null) {
                shufflerMap.values().forEach(shuffler -> {
                    if (shuffler.accountId != shuffling.getIssuerId()) {
                        try {
                            shuffler.submitRegister(shuffling);
                        } catch (RuntimeException e) {
                            LoggerUtil.logError(e.toString(), e);
                        }
                    }
                });
                clearExpiration(shuffling);
            }
        }, ShufflingEvent.SHUFFLING_CREATED);

        Shuffling.addShufflingListener(shuffling -> {
            Map<Long, Shuffler> shufflerMap = getShufflers(shuffling);
            if (shufflerMap != null) {
                Shuffler shuffler = shufflerMap.get(shuffling.getAssigneeAccountId());
                if (shuffler != null) {
                    try {
                        shuffler.submitProcess(shuffling);
                    } catch (RuntimeException e) {
                        LoggerUtil.logError(e.toString(), e);
                    }
                }
                clearExpiration(shuffling);
            }
        }, ShufflingEvent.SHUFFLING_PROCESSING_ASSIGNED);

        Shuffling.addShufflingListener(shuffling -> {
            Map<Long, Shuffler> shufflerMap = getShufflers(shuffling);
            if (shufflerMap != null) {
                shufflerMap.values().forEach(shuffler -> {
                    try {
                        shuffler.verify(shuffling);
                    } catch (RuntimeException e) {
                        LoggerUtil.logError(e.toString(), e);
                    }
                });
                clearExpiration(shuffling);
            }
        }, ShufflingEvent.SHUFFLING_PROCESSING_FINISHED);

        Shuffling.addShufflingListener(shuffling -> {
            Map<Long, Shuffler> shufflerMap = getShufflers(shuffling);
            if (shufflerMap != null) {
                shufflerMap.values().forEach(shuffler -> {
                    try {
                        shuffler.cancel(shuffling);
                    } catch (RuntimeException e) {
                        LoggerUtil.logError(e.toString(), e);
                    }
                });
                clearExpiration(shuffling);
            }
        }, ShufflingEvent.SHUFFLING_BLAME_STARTED);

        Shuffling.addShufflingListener(Shuffler::scheduleExpiration, ShufflingEvent.SHUFFLING_DONE);

        Shuffling.addShufflingListener(Shuffler::scheduleExpiration, ShufflingEvent.SHUFFLING_CANCELLED);

        EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
            Set<String> expired = EXPIRATIONS.get(block.getHeight());
            if (expired != null) {
                expired.forEach(SHUFFLINGS_MAP::remove);
                EXPIRATIONS.remove(block.getHeight());
            }
        }, EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);

        EcBlockchainProcessorImpl.getInstance().addECListener(block -> SHUFFLINGS_MAP.values().forEach(shufflerMap -> shufflerMap.values().forEach(shuffler -> {
            if (shuffler.failedTransaction != null) {
                try {
                    TransactionProcessorImpl.getInstance().broadcast(shuffler.failedTransaction);
                    shuffler.failedTransaction = null;
                    shuffler.failureCause = null;
                } catch (EcValidationException ignore) {
                }
            }
        })), EcBlockchainProcessorEvent.AFTER_BLOCK_ACCEPT);

        EcBlockchainProcessorImpl.getInstance().addECListener(block -> stopAllShufflers(), EcBlockchainProcessorEvent.RESCAN_BEGIN);

    }

    private final long accountId;
    private final String secretPhrase;
    private final byte[] recipientPublicKey;
    private final byte[] shufflingFullHash;
    private volatile Transaction failedTransaction;
    private volatile EcNotCurrentlyValidExceptionEc failureCause;

    private Shuffler(String secretPhrase, byte[] recipientPublicKey, byte[] shufflingFullHash) {
        this.secretPhrase = secretPhrase;
        this.accountId = Account.getId(Crypto.getPublicKey(secretPhrase));
        this.recipientPublicKey = recipientPublicKey;
        this.shufflingFullHash = shufflingFullHash;
    }

    public static Shuffler addOrGetShuffler(String secretPhrase, byte[] recipientPublicKey, byte[] shufflingFullHash) throws ShufflerException {
        String hash = Convert.toHexString(shufflingFullHash);
        long accountId = Account.getId(Crypto.getPublicKey(secretPhrase));
        EcBlockchainImpl.getInstance().writeLock();
        try {
            Map<Long, Shuffler> map = SHUFFLINGS_MAP.get(hash);
            if (map == null) {
                map = new HashMap<>();
                SHUFFLINGS_MAP.put(hash, map);
            }
            Shuffler shuffler = map.get(accountId);
            if (recipientPublicKey == null) {
                return shuffler;
            }
            if (SHUFFLINGS_MAP.size() > Constants.MAX_SHUFFLERS) {
                throw new ShufflerLimitException("Cannot run more than " + Constants.MAX_SHUFFLERS + " shufflers on the same node");
            }
            if (shuffler == null) {
                Shuffling shuffling = Shuffling.getShuffling(shufflingFullHash);
                if (shuffling == null && Account.getAccount(recipientPublicKey) != null) {
                    throw new InvalidRecipientException("Existing account cannot be used as shuffling recipient");
                }
                if (getRecipientShuffler(Account.getId(recipientPublicKey)) != null) {
                    throw new InvalidRecipientException("Another shuffler with the same recipient account already running");
                }
                if (map.size() >= (shuffling == null ? Constants.EC_MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS : shuffling.getParticipantCount())) {
                    throw new ShufflerLimitException("Cannot run shufflers for more than " + map.size() + " accounts for this shuffling");
                }
                Account account = Account.getAccount(accountId);
                if (account != null && account.getControls().contains(ControlType.PHASING_ONLY)) {
                    throw new ControlledAccountException("Cannot run a shuffler for an account under phasing only control");
                }
                shuffler = new Shuffler(secretPhrase, recipientPublicKey, shufflingFullHash);
                if (shuffling != null) {
                    shuffler.init(shuffling);
                    clearExpiration(shuffling);
                }
                map.put(accountId, shuffler);
                LoggerUtil.logInfo(String.format("Started shuffler for account %s, shuffling %s",
                        Long.toUnsignedString(accountId), Long.toUnsignedString(Convert.fullhashtoid(shufflingFullHash))));
            } else if (!Arrays.equals(shuffler.recipientPublicKey, recipientPublicKey)) {
                throw new DuplicateShufflerException("A shuffler with different recipientPublicKey already started");
            } else if (!Arrays.equals(shuffler.shufflingFullHash, shufflingFullHash)) {
                throw new DuplicateShufflerException("A shuffler with different shufflingFullHash already started");
            } else {
                LoggerUtil.logInfo("Shuffler already started");
            }
            return shuffler;
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    public static List<Shuffler> getAllShufflers() {
        List<Shuffler> shufflers = new ArrayList<>();
        EcBlockchainImpl.getInstance().readECLock();
        try {
            SHUFFLINGS_MAP.values().forEach(shufflerMap -> shufflers.addAll(shufflerMap.values()));
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return shufflers;
    }

    public static List<Shuffler> getShufflingShufflers(byte[] shufflingFullHash) {
        List<Shuffler> shufflers = new ArrayList<>();
        EcBlockchainImpl.getInstance().readECLock();
        try {
            Map<Long, Shuffler> shufflerMap = SHUFFLINGS_MAP.get(Convert.toHexString(shufflingFullHash));
            if (shufflerMap != null) {
                shufflers.addAll(shufflerMap.values());
            }
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return shufflers;
    }

    public static List<Shuffler> getAccountShufflers(long accountId) {
        List<Shuffler> shufflers = new ArrayList<>();
        EcBlockchainImpl.getInstance().readECLock();
        try {
            SHUFFLINGS_MAP.values().forEach(shufflerMap -> {
                Shuffler shuffler = shufflerMap.get(accountId);
                if (shuffler != null) {
                    shufflers.add(shuffler);
                }
            });
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return shufflers;
    }

    public static Shuffler getShuffler(long accountId, byte[] shufflingFullHash) {
        EcBlockchainImpl.getInstance().readECLock();
        try {
            Map<Long, Shuffler> shufflerMap = SHUFFLINGS_MAP.get(Convert.toHexString(shufflingFullHash));
            if (shufflerMap != null) {
                return shufflerMap.get(accountId);
            }
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return null;
    }

    public static Shuffler stopShuffler(long accountId, byte[] shufflingFullHash) {
        EcBlockchainImpl.getInstance().writeLock();
        try {
            Map<Long, Shuffler> shufflerMap = SHUFFLINGS_MAP.get(Convert.toHexString(shufflingFullHash));
            if (shufflerMap != null) {
                return shufflerMap.remove(accountId);
            }
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
        return null;
    }

    public static void stopAllShufflers() {
        EcBlockchainImpl.getInstance().writeLock();
        try {
            SHUFFLINGS_MAP.clear();
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    private static Shuffler getRecipientShuffler(long recipientId) {
        EcBlockchainImpl.getInstance().readECLock();
        try {
            for (Map<Long, Shuffler> shufflerMap : SHUFFLINGS_MAP.values()) {
                for (Shuffler shuffler : shufflerMap.values()) {
                    if (Account.getId(shuffler.recipientPublicKey) == recipientId) {
                        return shuffler;
                    }
                }
            }
            return null;
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
    }

    private static Map<Long, Shuffler> getShufflers(Shuffling shuffling) {
        return SHUFFLINGS_MAP.get(Convert.toHexString(shuffling.getFullHash()));
    }

    private static void scheduleExpiration(Shuffling shuffling) {
        int expirationHeight = EcBlockchainImpl.getInstance().getHeight() + 720;
        Set<String> shufflingIds = EXPIRATIONS.get(expirationHeight);
        if (shufflingIds == null) {
            shufflingIds = new HashSet<>();
            EXPIRATIONS.put(expirationHeight, shufflingIds);
        }
        shufflingIds.add(Convert.toHexString(shuffling.getFullHash()));
    }

    private static void clearExpiration(Shuffling shuffling) {
        for (Set shufflingIds : EXPIRATIONS.values()) {
            if (shufflingIds.remove(shuffling.getId())) {
                return;
            }
        }
    }

    public long getAccountId() {
        return accountId;
    }

    public byte[] getRecipientPublicKey() {
        return recipientPublicKey;
    }

    public byte[] getShufflingFullHash() {
        return shufflingFullHash;
    }

    public Transaction getFailedTransaction() {
        return failedTransaction;
    }

    public EcNotCurrentlyValidExceptionEc getFailureCause() {
        return failureCause;
    }

    private void init(Shuffling shuffling) throws ShufflerException {
        ShufflingParticipant shufflingParticipant = shuffling.getParticipant(accountId);
        switch (shuffling.getStage()) {
            case REGISTRATION:
                if (Account.getAccount(recipientPublicKey) != null) {
                    throw new InvalidRecipientException("Existing account cannot be used as shuffling recipient");
                }
                if (shufflingParticipant == null) {
                    submitRegister(shuffling);
                }
                break;
            case PROCESSING:
                if (shufflingParticipant == null) {
                    throw new InvalidStageException("Account has not registered for this shuffling");
                }
                if (Account.getAccount(recipientPublicKey) != null) {
                    throw new InvalidRecipientException("Existing account cannot be used as shuffling recipient");
                }
                if (accountId == shuffling.getAssigneeAccountId()) {
                    submitProcess(shuffling);
                }
                break;
            case VERIFICATION:
                if (shufflingParticipant == null) {
                    throw new InvalidStageException("Account has not registered for this shuffling");
                }
                if (shufflingParticipant.getState() == ShufflingParticipantState.PROCESSED) {
                    verify(shuffling);
                }
                break;
            case BLAME:
                if (shufflingParticipant == null) {
                    throw new InvalidStageException("Account has not registered for this shuffling");
                }
                if (shufflingParticipant.getState() != ShufflingParticipantState.CANCELLED) {
                    cancel(shuffling);
                }
                break;
            case DONE:
            case CANCELLED:
                scheduleExpiration(shuffling);
                break;
            default:
                throw new RuntimeException("Unsupported shuffling stage " + shuffling.getStage());
        }
        if (failureCause != null) {
            throw new ShufflerException(failureCause.getMessage(), failureCause);
        }
    }

    private void verify(Shuffling shuffling) {
        ShufflingParticipant shufflingParticipant = shuffling.getParticipant(accountId);
        if (shufflingParticipant != null && shufflingParticipant.getIndex() != shuffling.getParticipantCount() - 1) {
            boolean found = false;
            for (byte[] key : shuffling.getRecipientPublicKeys()) {
                if (Arrays.equals(key, recipientPublicKey)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                submitVerify(shuffling);
            } else {
                submitCancel(shuffling);
            }
        }
    }

    private void cancel(Shuffling shuffling) {
        if (accountId == shuffling.getAssigneeAccountId()) {
            return;
        }
        ShufflingParticipant shufflingParticipant = shuffling.getParticipant(accountId);
        if (shufflingParticipant == null || shufflingParticipant.getIndex() == shuffling.getParticipantCount() - 1) {
            return;
        }
        if (ShufflingParticipant.getData(shuffling.getId(), accountId) == null) {
            return;
        }
        submitCancel(shuffling);
    }

    private void submitRegister(Shuffling shuffling) {
        LoggerUtil.logDebug("Account " + Long.toUnsignedString(accountId) + " registering for shuffling " + Long.toUnsignedString(shuffling.getId()));
        Mortgaged.ShufflingRegistration attachment = new Mortgaged.ShufflingRegistration(shufflingFullHash);
        submitTransaction(attachment);
    }

    private void submitProcess(Shuffling shuffling) {
        LoggerUtil.logDebug("Account " + Long.toUnsignedString(accountId) + " processing shuffling " + Long.toUnsignedString(shuffling.getId()));
        Mortgaged.ShufflingMortgaged attachment = shuffling.process(accountId, secretPhrase, recipientPublicKey);
        submitTransaction(attachment);
    }

    private void submitVerify(Shuffling shuffling) {
        LoggerUtil.logDebug("Account " + Long.toUnsignedString(accountId) + " verifying shuffling " + Long.toUnsignedString(shuffling.getId()));
        Mortgaged.ShufflingVerification attachment = new Mortgaged.ShufflingVerification(shuffling.getId(), shuffling.getStateHash());
        submitTransaction(attachment);
    }

    private void submitCancel(Shuffling shuffling) {
        LoggerUtil.logDebug("Account " + Long.toUnsignedString(accountId) + " cancelling shuffling " + Long.toUnsignedString(shuffling.getId()));
        Mortgaged.ShufflingCancellation attachment = shuffling.revealKeySeeds(secretPhrase, shuffling.getAssigneeAccountId(), shuffling.getStateHash());
        submitTransaction(attachment);
    }

    private void submitTransaction(Mortgaged.ShufflingMortgaged attachment) {
        if (EcBlockchainProcessorImpl.getInstance().isProcessingBlock()) {
            if (hasUnconfirmedTransaction(attachment, TransactionProcessorImpl.getInstance().getUnconfirmedTransactionPriorityQueue())) {
                LoggerUtil.logDebug("Transaction already submitted");
                return;
            }
        } else {
            try (H2Iterator<UnconfirmedTransaction> unconfirmedTransactions = TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions()) {
                if (hasUnconfirmedTransaction(attachment, unconfirmedTransactions)) {
                    LoggerUtil.logDebug("Transaction already submitted");
                    return;
                }
            }
        }
        try {
            Builder builder = new TransactionImpl.BuilderImpl((byte) 1, Crypto.getPublicKey(secretPhrase), 0, 0,
                    (short) 1440, (Mortgaged.AbstractMortgaged) attachment);
            builder.timestamp(EcBlockchainImpl.getInstance().getLastBlockTimestamp());
            Transaction transaction = builder.build(secretPhrase);
            failedTransaction = null;
            failureCause = null;
            Account participantAccount = Account.getAccount(this.accountId);
            if (participantAccount == null || transaction.getFeeNQT() > participantAccount.getUnconfirmedBalanceNQT()) {
                failedTransaction = transaction;
                failureCause = new EcNotCurrentlyValidExceptionEc("Insufficient balance");
                LoggerUtil.logError("Error submitting shuffler transaction", failureCause);
            }
            try {
                TransactionProcessorImpl.getInstance().broadcast(transaction);
            } catch (EcNotCurrentlyValidExceptionEc e) {
                failedTransaction = transaction;
                failureCause = e;
                LoggerUtil.logError("Error submitting shuffler transaction", e);
            }
        } catch (EcValidationException e) {
            LoggerUtil.logError("Fatal error submitting shuffler transaction", e);
        }
    }

    private boolean hasUnconfirmedTransaction(Mortgaged.ShufflingMortgaged shufflingAttachment, Iterable<UnconfirmedTransaction> unconfirmedTransactions) {
        for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
            if (unconfirmedTransaction.getSenderId() != accountId) {
                continue;
            }
            Mortgaged mortgaged = unconfirmedTransaction.getAttachment();
            if (!mortgaged.getClass().equals(shufflingAttachment.getClass())) {
                continue;
            }
            if (Arrays.equals(shufflingAttachment.getShufflingStateHash(), ((Mortgaged.ShufflingMortgaged) mortgaged).getShufflingStateHash())) {
                return true;
            }
        }
        return false;
    }

    public static class ShufflerException extends EcException {

        private ShufflerException(String message) {
            super(message);
        }

        private ShufflerException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static final class ShufflerLimitException extends ShufflerException {

        private ShufflerLimitException(String message) {
            super(message);
        }

    }

    public static final class DuplicateShufflerException extends ShufflerException {

        private DuplicateShufflerException(String message) {
            super(message);
        }

    }

    public static final class InvalidRecipientException extends ShufflerException {

        private InvalidRecipientException(String message) {
            super(message);
        }

    }

    public static final class ControlledAccountException extends ShufflerException {

        private ControlledAccountException(String message) {
            super(message);
        }

    }

    public static final class InvalidStageException extends ShufflerException {

        private InvalidStageException(String message) {
            super(message);
        }

    }

}
