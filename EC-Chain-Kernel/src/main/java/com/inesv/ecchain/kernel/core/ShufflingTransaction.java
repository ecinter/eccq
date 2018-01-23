package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.EcTime;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ShufflingTransaction extends TransactionType {

    private static final byte SUBTYPE_SHUFFLING_CREATION = 0;
    public static final TransactionType SHUFFLING_CREATION = new ShufflingTransaction() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_SHUFFLING_CREATION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.SHUFFLING_REGISTRATION;
        }

        @Override
        public String getName() {
            return "ShufflingCreation";
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) {
            return new Mortgaged.ShufflingCreation(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) {
            return new Mortgaged.ShufflingCreation(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ShufflingCreation attachment = (Mortgaged.ShufflingCreation) transaction.getAttachment();
            HoldingType holdingType = attachment.getHoldingType();
            long amount = attachment.getAmount();
            if (holdingType == HoldingType.EC) {
                if (amount < Constants.EC_SHUFFLING_DEPOSIT_NQT || amount > Constants.EC_MAX_BALANCE_NQT) {
                    throw new EcNotValidExceptionEc("Invalid NQT amount " + amount
                            + ", minimum is " + Constants.EC_SHUFFLING_DEPOSIT_NQT);
                }
            } else if (holdingType == HoldingType.ASSET) {
                Property property = Property.getAsset(attachment.getHoldingId());
                if (property == null) {
                    throw new EcNotCurrentlyValidExceptionEc("Unknown property " + Long.toUnsignedString(attachment.getHoldingId()));
                }
                if (amount <= 0 || amount > property.getInitialQuantityQNT()) {
                    throw new EcNotValidExceptionEc("Invalid property quantity " + amount);
                }
            } else if (holdingType == HoldingType.CURRENCY) {
                Coin coin = Coin.getCoin(attachment.getHoldingId());
                CoinType.validate(coin, transaction);
                if (!coin.isActive()) {
                    throw new EcNotCurrentlyValidExceptionEc("Coin is not active: " + coin.getCoinCode());
                }
                if (amount <= 0 || amount > Constants.EC_MAX_CURRENCY_TOTAL_SUPPLY) {
                    throw new EcNotValidExceptionEc("Invalid coin amount " + amount);
                }
            } else {
                throw new RuntimeException("Unsupported holding type " + holdingType);
            }
            if (attachment.getParticipantCount() < Constants.EC_MIN_NUMBER_OF_SHUFFLING_PARTICIPANTS
                    || attachment.getParticipantCount() > Constants.EC_MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS) {
                throw new EcNotValidExceptionEc(String.format("Number of participants %d is not between %d and %d",
                        attachment.getParticipantCount(), Constants.EC_MIN_NUMBER_OF_SHUFFLING_PARTICIPANTS, Constants.EC_MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS));
            }
            if (attachment.getRegistrationPeriod() < 1 || attachment.getRegistrationPeriod() > Constants.EC_MAX_SHUFFLING_REGISTRATION_PERIOD) {
                throw new EcNotValidExceptionEc("Invalid registration period: " + attachment.getRegistrationPeriod());
            }
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ShufflingCreation attachment = (Mortgaged.ShufflingCreation) transaction.getAttachment();
            HoldingType holdingType = attachment.getHoldingType();
            if (holdingType != HoldingType.EC) {
                if (holdingType.getUnconfirmedBalance(senderAccount, attachment.getHoldingId()) >= attachment.getAmount()
                        && senderAccount.getUnconfirmedBalanceNQT() >= Constants.EC_SHUFFLING_DEPOSIT_NQT) {
                    holdingType.addToUnconfirmedBalance(senderAccount, getLedgerEvent(), transaction.getTransactionId(), attachment.getHoldingId(), -attachment.getAmount());
                    senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), -Constants.EC_SHUFFLING_DEPOSIT_NQT);
                    return true;
                }
            } else {
                if (senderAccount.getUnconfirmedBalanceNQT() >= attachment.getAmount()) {
                    senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), -attachment.getAmount());
                    return true;
                }
            }
            return false;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ShufflingCreation attachment = (Mortgaged.ShufflingCreation) transaction.getAttachment();
            Shuffling.addShuffling(transaction, attachment);
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ShufflingCreation attachment = (Mortgaged.ShufflingCreation) transaction.getAttachment();
            HoldingType holdingType = attachment.getHoldingType();
            if (holdingType != HoldingType.EC) {
                holdingType.addToUnconfirmedBalance(senderAccount, getLedgerEvent(), transaction.getTransactionId(), attachment.getHoldingId(), attachment.getAmount());
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), Constants.EC_SHUFFLING_DEPOSIT_NQT);
            } else {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), attachment.getAmount());
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ShufflingCreation attachment = (Mortgaged.ShufflingCreation) transaction.getAttachment();
            if (attachment.getHoldingType() != HoldingType.CURRENCY) {
                return false;
            }
            Coin coin = Coin.getCoin(attachment.getHoldingId());
            String nameLower = coin.getName().toLowerCase();
            String codeLower = coin.getCoinCode().toLowerCase();
            boolean isDuplicate = TransactionType.isDuplicate(Coinage.EC_CURRENCY_ISSUANCE, nameLower, duplicates, false);
            if (!nameLower.equals(codeLower)) {
                isDuplicate = isDuplicate || TransactionType.isDuplicate(Coinage.EC_CURRENCY_ISSUANCE, codeLower, duplicates, false);
            }
            return isDuplicate;
        }

    };
    private static final byte SUBTYPE_SHUFFLING_REGISTRATION = 1;
    public static final TransactionType SHUFFLING_REGISTRATION = new ShufflingTransaction() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_SHUFFLING_REGISTRATION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.SHUFFLING_REGISTRATION;
        }

        @Override
        public String getName() {
            return "ShufflingRegistration";
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) {
            return new Mortgaged.ShufflingRegistration(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) {
            return new Mortgaged.ShufflingRegistration(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ShufflingRegistration attachment = (Mortgaged.ShufflingRegistration) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            if (shuffling == null) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling not found: " + Long.toUnsignedString(attachment.getShufflingId()));
            }
            byte[] shufflingStateHash = shuffling.getStateHash();
            if (shufflingStateHash == null || !Arrays.equals(shufflingStateHash, attachment.getShufflingStateHash())) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling state hash doesn't match");
            }
            if (shuffling.getStage() != Shuffling.Stage.REGISTRATION) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling registration has ended for " + Long.toUnsignedString(attachment.getShufflingId()));
            }
            if (shuffling.getParticipant(transaction.getSenderId()) != null) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Account %s is already registered for shuffling %s",
                        Long.toUnsignedString(transaction.getSenderId()), Long.toUnsignedString(shuffling.getId())));
            }
            if (EcBlockchainImpl.getInstance().getHeight() + shuffling.getBlocksRemaining() <= attachment.getFinishValidationHeight(transaction)) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling registration finishes in " + shuffling.getBlocksRemaining() + " blocks");
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ShufflingRegistration attachment = (Mortgaged.ShufflingRegistration) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            return TransactionType.isDuplicate(SHUFFLING_REGISTRATION,
                    Long.toUnsignedString(shuffling.getId()) + "." + Long.toUnsignedString(transaction.getSenderId()), duplicates, true)
                    || TransactionType.isDuplicate(SHUFFLING_REGISTRATION,
                    Long.toUnsignedString(shuffling.getId()), duplicates, shuffling.getParticipantCount() - shuffling.getRegistrantCount());
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ShufflingRegistration attachment = (Mortgaged.ShufflingRegistration) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            HoldingType holdingType = shuffling.getHoldingType();
            if (holdingType != HoldingType.EC) {
                if (holdingType.getUnconfirmedBalance(senderAccount, shuffling.getHoldingId()) >= shuffling.getAmount()
                        && senderAccount.getUnconfirmedBalanceNQT() >= Constants.EC_SHUFFLING_DEPOSIT_NQT) {
                    holdingType.addToUnconfirmedBalance(senderAccount, getLedgerEvent(), transaction.getTransactionId(), shuffling.getHoldingId(), -shuffling.getAmount());
                    senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), -Constants.EC_SHUFFLING_DEPOSIT_NQT);
                    return true;
                }
            } else {
                if (senderAccount.getUnconfirmedBalanceNQT() >= shuffling.getAmount()) {
                    senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), -shuffling.getAmount());
                    return true;
                }
            }
            return false;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ShufflingRegistration attachment = (Mortgaged.ShufflingRegistration) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            shuffling.addParticipant(transaction.getSenderId());
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ShufflingRegistration attachment = (Mortgaged.ShufflingRegistration) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            HoldingType holdingType = shuffling.getHoldingType();
            if (holdingType != HoldingType.EC) {
                holdingType.addToUnconfirmedBalance(senderAccount, getLedgerEvent(), transaction.getTransactionId(), shuffling.getHoldingId(), shuffling.getAmount());
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), Constants.EC_SHUFFLING_DEPOSIT_NQT);
            } else {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), shuffling.getAmount());
            }
        }

    };
    private static final byte SUBTYPE_SHUFFLING_PROCESSING = 2;
    private static final byte SUBTYPE_SHUFFLING_RECIPIENTS = 3;
    private static final byte SUBTYPE_SHUFFLING_VERIFICATION = 4;
    public static final TransactionType SHUFFLING_VERIFICATION = new ShufflingTransaction() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_SHUFFLING_VERIFICATION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.SHUFFLING_PROCESSING;
        }

        @Override
        public String getName() {
            return "ShufflingVerification";
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) {
            return new Mortgaged.ShufflingVerification(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) {
            return new Mortgaged.ShufflingVerification(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ShufflingVerification attachment = (Mortgaged.ShufflingVerification) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            if (shuffling == null) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling not found: " + Long.toUnsignedString(attachment.getShufflingId()));
            }
            if (shuffling.getStage() != Shuffling.Stage.VERIFICATION) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling not in verification stage: " + Long.toUnsignedString(attachment.getShufflingId()));
            }
            ShufflingParticipant participant = shuffling.getParticipant(transaction.getSenderId());
            if (participant == null) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Account %s is not registered for shuffling %s",
                        Long.toUnsignedString(transaction.getSenderId()), Long.toUnsignedString(shuffling.getId())));
            }
            if (!participant.getState().canBecome(ShufflingParticipantState.VERIFIED)) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Shuffling participant %s in state %s cannot become verified",
                        Long.toUnsignedString(attachment.getShufflingId()), participant.getState()));
            }
            if (participant.getIndex() == shuffling.getParticipantCount() - 1) {
                throw new EcNotValidExceptionEc("Last participant cannot submit verification transaction");
            }
            byte[] shufflingStateHash = shuffling.getStateHash();
            if (shufflingStateHash == null || !Arrays.equals(shufflingStateHash, attachment.getShufflingStateHash())) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling state hash doesn't match");
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ShufflingVerification attachment = (Mortgaged.ShufflingVerification) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            return TransactionType.isDuplicate(SHUFFLING_VERIFICATION,
                    Long.toUnsignedString(shuffling.getId()) + "." + Long.toUnsignedString(transaction.getSenderId()), duplicates, true);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ShufflingVerification attachment = (Mortgaged.ShufflingVerification) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            shuffling.verify(transaction.getSenderId());
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public boolean isPhasable() {
            return false;
        }

    };
    private static final byte SUBTYPE_SHUFFLING_CANCELLATION = 5;
    private final static Fee SHUFFLING_PROCESSING_FEE = new Fee.ConstantFee(10 * Constants.ONE_EC);
    public static final TransactionType SHUFFLING_PROCESSING = new ShufflingTransaction() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_SHUFFLING_PROCESSING;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.SHUFFLING_PROCESSING;
        }

        @Override
        public String getName() {
            return "ShufflingProcessing";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return SHUFFLING_PROCESSING_FEE;
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ShufflingProcessing(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ShufflingProcessing(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ShufflingProcessing attachment = (Mortgaged.ShufflingProcessing) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            if (shuffling == null) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling not found: " + Long.toUnsignedString(attachment.getShufflingId()));
            }
            if (shuffling.getStage() != Shuffling.Stage.PROCESSING) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Shuffling %s is not in processing stage",
                        Long.toUnsignedString(attachment.getShufflingId())));
            }
            ShufflingParticipant participant = shuffling.getParticipant(transaction.getSenderId());
            if (participant == null) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Account %s is not registered for shuffling %s",
                        Long.toUnsignedString(transaction.getSenderId()), Long.toUnsignedString(shuffling.getId())));
            }
            if (!participant.getState().canBecome(ShufflingParticipantState.PROCESSED)) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Participant %s processing already complete",
                        Long.toUnsignedString(transaction.getSenderId())));
            }
            if (participant.getAccountId() != shuffling.getAssigneeAccountId()) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Participant %s is not currently assigned to process shuffling %s",
                        Long.toUnsignedString(participant.getAccountId()), Long.toUnsignedString(shuffling.getId())));
            }
            if (participant.getNextAccountId() == 0) {
                throw new EcNotValidExceptionEc(String.format("Participant %s is last in shuffle",
                        Long.toUnsignedString(transaction.getSenderId())));
            }
            byte[] shufflingStateHash = shuffling.getStateHash();
            if (shufflingStateHash == null || !Arrays.equals(shufflingStateHash, attachment.getShufflingStateHash())) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling state hash doesn't match");
            }
            byte[][] data = attachment.getData();
            if (data == null && new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MIN_PRUNABLE_LIFETIME) {
                throw new EcNotCurrentlyValidExceptionEc("Data has been pruned prematurely");
            }
            if (data != null) {
                if (data.length != participant.getIndex() + 1 && data.length != 0) {
                    throw new EcNotValidExceptionEc(String.format("Invalid number of encrypted data %d for participant number %d",
                            data.length, participant.getIndex()));
                }
                byte[] previous = null;
                for (byte[] bytes : data) {
                    if (bytes.length != 32 + 64 * (shuffling.getParticipantCount() - participant.getIndex() - 1)) {
                        throw new EcNotValidExceptionEc("Invalid encrypted data length " + bytes.length);
                    }
                    if (previous != null && Convert.byteArrayComparator.compare(previous, bytes) >= 0) {
                        throw new EcNotValidExceptionEc("Duplicate or unsorted encrypted data");
                    }
                    previous = bytes;
                }
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ShufflingProcessing attachment = (Mortgaged.ShufflingProcessing) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            return TransactionType.isDuplicate(SHUFFLING_PROCESSING, Long.toUnsignedString(shuffling.getId()), duplicates, true);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ShufflingProcessing attachment = (Mortgaged.ShufflingProcessing) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            shuffling.updateParticipantData(transaction, attachment);
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public boolean isPhasable() {
            return false;
        }

        @Override
        boolean isPruned(long transactionId) {
            Transaction transaction = TransactionH2.selectTransaction(transactionId);
            Mortgaged.ShufflingProcessing attachment = (Mortgaged.ShufflingProcessing) transaction.getAttachment();
            return ShufflingParticipant.getData(attachment.getShufflingId(), transaction.getSenderId()) == null;
        }

    };
    public static final TransactionType SHUFFLING_CANCELLATION = new ShufflingTransaction() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_SHUFFLING_CANCELLATION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.SHUFFLING_PROCESSING;
        }

        @Override
        public String getName() {
            return "ShufflingCancellation";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return SHUFFLING_PROCESSING_FEE;
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ShufflingCancellation(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) {
            return new Mortgaged.ShufflingCancellation(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ShufflingCancellation attachment = (Mortgaged.ShufflingCancellation) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            if (shuffling == null) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling not found: " + Long.toUnsignedString(attachment.getShufflingId()));
            }
            long cancellingAccountId = attachment.getCancellingAccountId();
            if (cancellingAccountId == 0 && !shuffling.getStage().canBecome(Shuffling.Stage.BLAME)) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Shuffling in state %s cannot be cancelled", shuffling.getStage()));
            }
            if (cancellingAccountId != 0 && cancellingAccountId != shuffling.getAssigneeAccountId()) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Shuffling %s is not currently being cancelled by account %s",
                        Long.toUnsignedString(shuffling.getId()), Long.toUnsignedString(cancellingAccountId)));
            }
            ShufflingParticipant participant = shuffling.getParticipant(transaction.getSenderId());
            if (participant == null) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Account %s is not registered for shuffling %s",
                        Long.toUnsignedString(transaction.getSenderId()), Long.toUnsignedString(shuffling.getId())));
            }
            if (!participant.getState().canBecome(ShufflingParticipantState.CANCELLED)) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Shuffling participant %s in state %s cannot submit cancellation",
                        Long.toUnsignedString(attachment.getShufflingId()), participant.getState()));
            }
            if (participant.getIndex() == shuffling.getParticipantCount() - 1) {
                throw new EcNotValidExceptionEc("Last participant cannot submit cancellation transaction");
            }
            byte[] shufflingStateHash = shuffling.getStateHash();
            if (shufflingStateHash == null || !Arrays.equals(shufflingStateHash, attachment.getShufflingStateHash())) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling state hash doesn't match");
            }
            Transaction dataProcessingTransaction = TransactionH2.selectTransactionByFullHash(participant.getDataTransactionFullHash(), EcBlockchainImpl.getInstance().getHeight());
            if (dataProcessingTransaction == null) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid data transaction full hash");
            }
            Mortgaged.ShufflingProcessing shufflingProcessing = (Mortgaged.ShufflingProcessing) dataProcessingTransaction.getAttachment();
            if (!Arrays.equals(shufflingProcessing.getHash(), attachment.getHash())) {
                throw new EcNotValidExceptionEc("Blame data hash doesn't match processing data hash");
            }
            byte[][] keySeeds = attachment.getKeySeeds();
            if (keySeeds.length != shuffling.getParticipantCount() - participant.getIndex() - 1) {
                throw new EcNotValidExceptionEc("Invalid number of revealed keySeeds: " + keySeeds.length);
            }
            for (byte[] keySeed : keySeeds) {
                if (keySeed.length != 32) {
                    throw new EcNotValidExceptionEc("Invalid keySeed: " + Convert.toHexString(keySeed));
                }
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ShufflingCancellation attachment = (Mortgaged.ShufflingCancellation) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            return TransactionType.isDuplicate(SHUFFLING_VERIFICATION, // use VERIFICATION for unique type
                    Long.toUnsignedString(shuffling.getId()) + "." + Long.toUnsignedString(transaction.getSenderId()), duplicates, true);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ShufflingCancellation attachment = (Mortgaged.ShufflingCancellation) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            ShufflingParticipant participant = ShufflingParticipant.getParticipant(shuffling.getId(), senderAccount.getId());
            shuffling.cancelBy(participant, attachment.getBlameData(), attachment.getKeySeeds());
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public boolean isPhasable() {
            return false;
        }

    };
    private final static Fee SHUFFLING_RECIPIENTS_FEE = new Fee.ConstantFee(11 * Constants.ONE_EC);
    public static final TransactionType SHUFFLING_RECIPIENTS = new ShufflingTransaction() {

        @Override
        public byte getSubtype() {
            return SUBTYPE_SHUFFLING_RECIPIENTS;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.SHUFFLING_PROCESSING;
        }

        @Override
        public String getName() {
            return "ShufflingRecipients";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return SHUFFLING_RECIPIENTS_FEE;
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ShufflingRecipients(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) {
            return new Mortgaged.ShufflingRecipients(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ShufflingRecipients attachment = (Mortgaged.ShufflingRecipients) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            if (shuffling == null) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling not found: " + Long.toUnsignedString(attachment.getShufflingId()));
            }
            if (shuffling.getStage() != Shuffling.Stage.PROCESSING) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Shuffling %s is not in processing stage",
                        Long.toUnsignedString(attachment.getShufflingId())));
            }
            ShufflingParticipant participant = shuffling.getParticipant(transaction.getSenderId());
            if (participant == null) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Account %s is not registered for shuffling %s",
                        Long.toUnsignedString(transaction.getSenderId()), Long.toUnsignedString(shuffling.getId())));
            }
            if (participant.getNextAccountId() != 0) {
                throw new EcNotValidExceptionEc(String.format("Participant %s is not last in shuffle",
                        Long.toUnsignedString(transaction.getSenderId())));
            }
            if (!participant.getState().canBecome(ShufflingParticipantState.PROCESSED)) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Participant %s processing already complete",
                        Long.toUnsignedString(transaction.getSenderId())));
            }
            if (participant.getAccountId() != shuffling.getAssigneeAccountId()) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Participant %s is not currently assigned to process shuffling %s",
                        Long.toUnsignedString(participant.getAccountId()), Long.toUnsignedString(shuffling.getId())));
            }
            byte[] shufflingStateHash = shuffling.getStateHash();
            if (shufflingStateHash == null || !Arrays.equals(shufflingStateHash, attachment.getShufflingStateHash())) {
                throw new EcNotCurrentlyValidExceptionEc("Shuffling state hash doesn't match");
            }
            byte[][] recipientPublicKeys = attachment.getRecipientPublicKeys();
            if (recipientPublicKeys.length != shuffling.getParticipantCount() && recipientPublicKeys.length != 0) {
                throw new EcNotValidExceptionEc(String.format("Invalid number of recipient public keys %d", recipientPublicKeys.length));
            }
            Set<Long> recipientAccounts = new HashSet<>(recipientPublicKeys.length);
            for (byte[] recipientPublicKey : recipientPublicKeys) {
                if (!Crypto.isCanonicalPublicKey(recipientPublicKey)) {
                    throw new EcNotValidExceptionEc("Invalid recipient public key " + Convert.toHexString(recipientPublicKey));
                }
                if (!recipientAccounts.add(Account.getId(recipientPublicKey))) {
                    throw new EcNotValidExceptionEc("Duplicate recipient accounts");
                }
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ShufflingRecipients attachment = (Mortgaged.ShufflingRecipients) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            return TransactionType.isDuplicate(SHUFFLING_PROCESSING, Long.toUnsignedString(shuffling.getId()), duplicates, true);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ShufflingRecipients attachment = (Mortgaged.ShufflingRecipients) transaction.getAttachment();
            Shuffling shuffling = Shuffling.getShuffling(attachment.getShufflingId());
            shuffling.updateRecipients(transaction, attachment);
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public boolean isPhasable() {
            return false;
        }

    };

    private ShufflingTransaction() {
    }

    static TransactionType findTransactionType(byte subtype) {
        switch (subtype) {
            case SUBTYPE_SHUFFLING_CREATION:
                return SHUFFLING_CREATION;
            case SUBTYPE_SHUFFLING_REGISTRATION:
                return SHUFFLING_REGISTRATION;
            case SUBTYPE_SHUFFLING_PROCESSING:
                return SHUFFLING_PROCESSING;
            case SUBTYPE_SHUFFLING_RECIPIENTS:
                return SHUFFLING_RECIPIENTS;
            case SUBTYPE_SHUFFLING_VERIFICATION:
                return SHUFFLING_VERIFICATION;
            case SUBTYPE_SHUFFLING_CANCELLATION:
                return SHUFFLING_CANCELLATION;
            default:
                return null;
        }
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_SHUFFLING;
    }

    @Override
    public final boolean canHaveRecipient() {
        return false;
    }

    @Override
    public final boolean isPhasingSafe() {
        return false;
    }

}
