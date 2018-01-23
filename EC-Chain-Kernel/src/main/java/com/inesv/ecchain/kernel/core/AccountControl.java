package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;


public abstract class AccountControl extends TransactionType {

    public static final TransactionType EFFECTIVE_BALANCE_LEASING = new com.inesv.ecchain.kernel.core.AccountControl() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
        }

        @Override
        public String getName() {
            return "EffectiveBalanceLeasing";
        }

        @Override
        Mortgaged.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.AccountControlEffectiveBalanceLeasing(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AccountControlEffectiveBalanceLeasing parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.AccountControlEffectiveBalanceLeasing(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.AccountControlEffectiveBalanceLeasing attachment = (Mortgaged.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
            Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(), attachment.getPeriod());
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.AccountControlEffectiveBalanceLeasing attachment = (Mortgaged.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
            if (transaction.getSenderId() == transaction.getRecipientId()) {
                throw new EcNotValidExceptionEc("Account cannot lease balance to itself");
            }
            if (transaction.getAmountNQT() != 0) {
                throw new EcNotValidExceptionEc("Transaction amount must be 0 for effective balance leasing");
            }
            if (attachment.getPeriod() < Constants.EC_LEASING_DELAY || attachment.getPeriod() > 65535) {
                throw new EcNotValidExceptionEc("Invalid effective balance leasing period: " + attachment.getPeriod());
            }
            byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
            if (recipientPublicKey == null && EcBlockchainImpl.getInstance().getHeight() > Constants.EC_PHASING_BLOCK) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid effective balance leasing: "
                        + " recipient account " + Long.toUnsignedString(transaction.getRecipientId()) + " not found or no public key published");
            }
            if (transaction.getRecipientId() == Genesis.EC_CREATOR_ID) {
                throw new EcNotValidExceptionEc("Leasing to Genesis account not allowed");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };
    public static final TransactionType SET_PHASING_ONLY = new com.inesv.ecchain.kernel.core.AccountControl() {

        @Override
        public byte getSubtype() {
            return Constants.SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ACCOUNT_CONTROL_PHASING_ONLY;
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) {
            return new Mortgaged.SetPhasingOnly(buffer, transactionVersion);
        }

        @Override
        Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) {
            return new Mortgaged.SetPhasingOnly(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.SetPhasingOnly attachment = (Mortgaged.SetPhasingOnly) transaction.getAttachment();
            VoteWeighting.VotingModel votingModel = attachment.getPhasingParams().getVoteWeighting().getVotingModel();
            attachment.getPhasingParams().validate();
            if (votingModel == VoteWeighting.VotingModel.NONE) {
                Account senderAccount = Account.getAccount(transaction.getSenderId());
                if (senderAccount == null || !senderAccount.getControls().contains(ControlType.PHASING_ONLY)) {
                    throw new EcNotCurrentlyValidExceptionEc("Phasing only account control is not currently enabled");
                }
            } else if (votingModel == VoteWeighting.VotingModel.TRANSACTION || votingModel == VoteWeighting.VotingModel.HASH) {
                throw new EcNotValidExceptionEc("Invalid voting model " + votingModel + " for account control");
            }
            long maxFees = attachment.getMaxFees();
            long maxFeesLimit = (attachment.getPhasingParams().getVoteWeighting().isBalanceIndependent() ? 3 : 22) * Constants.ONE_EC;
            if (maxFees < 0 || (maxFees > 0 && maxFees < maxFeesLimit) || maxFees > Constants.EC_MAX_BALANCE_NQT) {
                throw new EcNotValidExceptionEc(String.format("Invalid max fees %f EC", ((double) maxFees) / Constants.ONE_EC));
            }
            short minDuration = attachment.getMinDuration();
            if (minDuration < 0 || (minDuration > 0 && minDuration < 3) || minDuration >= Constants.EC_MAX_PHASING_DURATION) {
                throw new EcNotValidExceptionEc("Invalid min duration " + attachment.getMinDuration());
            }
            short maxDuration = attachment.getMaxDuration();
            if (maxDuration < 0 || (maxDuration > 0 && maxDuration < 3) || maxDuration >= Constants.EC_MAX_PHASING_DURATION) {
                throw new EcNotValidExceptionEc("Invalid max duration " + maxDuration);
            }
            if (minDuration > maxDuration) {
                throw new EcNotValidExceptionEc(String.format("Min duration %d cannot exceed max duration %d ",
                        minDuration, maxDuration));
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return TransactionType.isDuplicate(SET_PHASING_ONLY, Long.toUnsignedString(transaction.getSenderId()), duplicates, true);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.SetPhasingOnly attachment = (Mortgaged.SetPhasingOnly) transaction.getAttachment();
            AccountRestrictions.PhasingOnly.set(senderAccount, attachment);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public String getName() {
            return "SetPhasingOnly";
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };

    private AccountControl() {
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_ACCOUNT_CONTROL;
    }

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
    }

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

}
