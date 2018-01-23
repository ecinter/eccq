package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public abstract class Messaging extends TransactionType {

    public final static TransactionType ARBITRARY_MESSAGE = new com.inesv.ecchain.kernel.core.Messaging() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ARBITRARY_MESSAGE;
        }

        @Override
        public String getName() {
            return "ArbitraryMessage";
        }

        @Override
        Mortgaged.EmptyMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return Mortgaged.ARBITRARY_MESSAGE;
        }

        @Override
        Mortgaged.EmptyMortgaged parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return Mortgaged.ARBITRARY_MESSAGE;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged mortgaged = transaction.getAttachment();
            if (transaction.getAmountNQT() != 0) {
                throw new EcNotValidExceptionEc("Invalid arbitrary message: " + mortgaged.getJSONObject());
            }
            if (transaction.getRecipientId() == Genesis.EC_CREATOR_ID && EcBlockchainImpl.getInstance().getHeight() > Constants.EC_MONETARY_SYSTEM_BLOCK) {
                throw new EcNotValidExceptionEc("Sending messages to Genesis not allowed.");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean mustHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };
    public static final TransactionType ALIAS_ASSIGNMENT = new com.inesv.ecchain.kernel.core.Messaging() {

        private final Fee ALIAS_FEE = new Fee.SizeBasedFee(2 * Constants.ONE_EC, 2 * Constants.ONE_EC, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                Mortgaged.MessagingAliasAssignment attachment = (Mortgaged.MessagingAliasAssignment) transaction.getAttachment();
                return attachment.getAliasName().length() + attachment.getAliasURI().length();
            }
        };

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ALIAS_ASSIGNMENT;
        }

        @Override
        public String getName() {
            return "AliasAssignment";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return ALIAS_FEE;
        }

        @Override
        Mortgaged.MessagingAliasAssignment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasAssignment(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingAliasAssignment parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasAssignment(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingAliasAssignment attachment = (Mortgaged.MessagingAliasAssignment) transaction.getAttachment();
            AccountName.addOrUpdateAlias(transaction, attachment);
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MessagingAliasAssignment attachment = (Mortgaged.MessagingAliasAssignment) transaction.getAttachment();
            return isDuplicate(com.inesv.ecchain.kernel.core.Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK
                    && AccountName.getAlias(((Mortgaged.MessagingAliasAssignment) transaction.getAttachment()).getAliasName()) == null
                    && isDuplicate(com.inesv.ecchain.kernel.core.Messaging.ALIAS_ASSIGNMENT, "", duplicates, true);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MessagingAliasAssignment attachment = (Mortgaged.MessagingAliasAssignment) transaction.getAttachment();
            if (attachment.getAliasName().length() == 0
                    || attachment.getAliasName().length() > Constants.EC_MAX_ALIAS_LENGTH
                    || attachment.getAliasURI().length() > Constants.EC_MAX_ALIAS_URI_LENGTH) {
                throw new EcNotValidExceptionEc("Invalid accountName assignment: " + attachment.getJSONObject());
            }
            String normalizedAlias = attachment.getAliasName().toLowerCase();
            for (int i = 0; i < normalizedAlias.length(); i++) {
                if (Constants.EC_ALPHABET.indexOf(normalizedAlias.charAt(i)) < 0) {
                    throw new EcNotValidExceptionEc("Invalid accountName name: " + normalizedAlias);
                }
            }
            AccountName accountName = AccountName.getAlias(normalizedAlias);
            if (accountName != null && accountName.getAccountId() != transaction.getSenderId()) {
                throw new EcNotCurrentlyValidExceptionEc("AccountName already owned by another account: " + normalizedAlias);
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };
    public static final TransactionType ALIAS_SELL = new com.inesv.ecchain.kernel.core.Messaging() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ALIAS_SELL;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ALIAS_SELL;
        }

        @Override
        public String getName() {
            return "AliasSell";
        }

        @Override
        Mortgaged.MessagingAliasSell parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasSell(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingAliasSell parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasSell(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingAliasSell attachment = (Mortgaged.MessagingAliasSell) transaction.getAttachment();
            AccountName.sellAlias(transaction, attachment);
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MessagingAliasSell attachment = (Mortgaged.MessagingAliasSell) transaction.getAttachment();
            // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
            return isDuplicate(com.inesv.ecchain.kernel.core.Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            if (transaction.getAmountNQT() != 0) {
                throw new EcNotValidExceptionEc("Invalid sell accountName transaction: " +
                        transaction.getJSONObject());
            }
            final Mortgaged.MessagingAliasSell attachment =
                    (Mortgaged.MessagingAliasSell) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            if (aliasName == null || aliasName.length() == 0) {
                throw new EcNotValidExceptionEc("Missing accountName name");
            }
            long priceNQT = attachment.getPriceNQT();
            if (priceNQT < 0 || priceNQT > Constants.EC_MAX_BALANCE_NQT) {
                throw new EcNotValidExceptionEc("Invalid accountName sell price: " + priceNQT);
            }
            if (priceNQT == 0) {
                if (Genesis.EC_CREATOR_ID == transaction.getRecipientId()) {
                    throw new EcNotValidExceptionEc("Transferring aliases to Genesis account not allowed");
                } else if (transaction.getRecipientId() == 0) {
                    throw new EcNotValidExceptionEc("Missing accountName transfer recipient");
                }
            }
            final AccountName accountName = AccountName.getAlias(aliasName);
            if (accountName == null) {
                throw new EcNotCurrentlyValidExceptionEc("No such accountName: " + aliasName);
            } else if (accountName.getAccountId() != transaction.getSenderId()) {
                throw new EcNotCurrentlyValidExceptionEc("AccountName doesn't belong to sender: " + aliasName);
            }
            if (transaction.getRecipientId() == Genesis.EC_CREATOR_ID) {
                throw new EcNotValidExceptionEc("Selling accountName to Genesis not allowed");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean mustHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };
    public static final TransactionType ALIAS_BUY = new com.inesv.ecchain.kernel.core.Messaging() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ALIAS_BUY;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ALIAS_BUY;
        }

        @Override
        public String getName() {
            return "AliasBuy";
        }

        @Override
        Mortgaged.MessagingAliasBuy parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasBuy(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingAliasBuy parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasBuy(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            final Mortgaged.MessagingAliasBuy attachment =
                    (Mortgaged.MessagingAliasBuy) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            AccountName.changeOwner(transaction.getSenderId(), aliasName);
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MessagingAliasBuy attachment = (Mortgaged.MessagingAliasBuy) transaction.getAttachment();
            // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
            return isDuplicate(com.inesv.ecchain.kernel.core.Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            final Mortgaged.MessagingAliasBuy attachment =
                    (Mortgaged.MessagingAliasBuy) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            final AccountName accountName = AccountName.getAlias(aliasName);
            if (accountName == null) {
                throw new EcNotCurrentlyValidExceptionEc("No such accountName: " + aliasName);
            } else if (accountName.getAccountId() != transaction.getRecipientId()) {
                throw new EcNotCurrentlyValidExceptionEc("AccountName is owned by account other than recipient: "
                        + Long.toUnsignedString(accountName.getAccountId()));
            }
            AccountName.Offer offer = AccountName.getOffer(accountName);
            if (offer == null) {
                throw new EcNotCurrentlyValidExceptionEc("AccountName is not for sale: " + aliasName);
            }
            if (transaction.getAmountNQT() < offer.getPriceNQT()) {
                String msg = "Price is too low for: " + aliasName + " ("
                        + transaction.getAmountNQT() + " < " + offer.getPriceNQT() + ")";
                throw new EcNotCurrentlyValidExceptionEc(msg);
            }
            if (offer.getBuyerId() != 0 && offer.getBuyerId() != transaction.getSenderId()) {
                throw new EcNotCurrentlyValidExceptionEc("Wrong buyer for " + aliasName + ": "
                        + Long.toUnsignedString(transaction.getSenderId()) + " expected: "
                        + Long.toUnsignedString(offer.getBuyerId()));
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };
    public static final TransactionType ALIAS_DELETE = new com.inesv.ecchain.kernel.core.Messaging() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ALIAS_DELETE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ALIAS_DELETE;
        }

        @Override
        public String getName() {
            return "AliasDelete";
        }

        @Override
        Mortgaged.MessagingAliasDelete parseAttachment(final ByteBuffer buffer, final byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasDelete(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingAliasDelete parseAttachment(final JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAliasDelete(attachmentData);
        }

        @Override
        void applyAttachment(final Transaction transaction, final Account senderAccount, final Account recipientAccount) {
            final Mortgaged.MessagingAliasDelete attachment =
                    (Mortgaged.MessagingAliasDelete) transaction.getAttachment();
            AccountName.deleteAlias(attachment.getAliasName());
        }

        @Override
        boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MessagingAliasDelete attachment = (Mortgaged.MessagingAliasDelete) transaction.getAttachment();
            // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
            return isDuplicate(com.inesv.ecchain.kernel.core.Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
        }

        @Override
        void validateAttachment(final Transaction transaction) throws EcValidationException {
            final Mortgaged.MessagingAliasDelete attachment =
                    (Mortgaged.MessagingAliasDelete) transaction.getAttachment();
            final String aliasName = attachment.getAliasName();
            if (aliasName == null || aliasName.length() == 0) {
                throw new EcNotValidExceptionEc("Missing accountName name");
            }
            final AccountName accountName = AccountName.getAlias(aliasName);
            if (accountName == null) {
                throw new EcNotCurrentlyValidExceptionEc("No such accountName: " + aliasName);
            } else if (accountName.getAccountId() != transaction.getSenderId()) {
                throw new EcNotCurrentlyValidExceptionEc("AccountName doesn't belong to sender: " + aliasName);
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };
    public final static TransactionType POLL_CREATION = new com.inesv.ecchain.kernel.core.Messaging() {

        private final Fee POLL_OPTIONS_FEE = new Fee.SizeBasedFee(10 * Constants.ONE_EC, Constants.ONE_EC, 1) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                int numOptions = ((Mortgaged.MessagingPollCreation) appendage).getPollOptions().length;
                return numOptions <= 19 ? 0 : numOptions - 19;
            }
        };

        private final Fee POLL_SIZE_FEE = new Fee.SizeBasedFee(0, 2 * Constants.ONE_EC, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                Mortgaged.MessagingPollCreation attachment = (Mortgaged.MessagingPollCreation) appendage;
                int size = attachment.getPollName().length() + attachment.getPollDescription().length();
                for (String option : ((Mortgaged.MessagingPollCreation) appendage).getPollOptions()) {
                    size += option.length();
                }
                return size <= 288 ? 0 : size - 288;
            }
        };

        private final Fee POLL_FEE = (transaction, appendage) ->
                POLL_OPTIONS_FEE.getFee(transaction, appendage) + POLL_SIZE_FEE.getFee(transaction, appendage);

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_POLL_CREATION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.POLL_CREATION;
        }

        @Override
        public String getName() {
            return "PollCreation";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return POLL_FEE;
        }

        @Override
        Mortgaged.MessagingPollCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingPollCreation(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingPollCreation parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingPollCreation(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingPollCreation attachment = (Mortgaged.MessagingPollCreation) transaction.getAttachment();
            Poll.addPoll(transaction, attachment);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {

            Mortgaged.MessagingPollCreation attachment = (Mortgaged.MessagingPollCreation) transaction.getAttachment();

            int optionsCount = attachment.getPollOptions().length;

            if (attachment.getPollName().length() > Constants.EC_MAX_POLL_NAME_LENGTH
                    || attachment.getPollName().isEmpty()
                    || attachment.getPollDescription().length() > Constants.EC_MAX_POLL_DESCRIPTION_LENGTH
                    || optionsCount > Constants.EC_MAX_POLL_OPTION_COUNT
                    || optionsCount == 0) {
                throw new EcNotValidExceptionEc("Invalid poll attachment: " + attachment.getJSONObject());
            }

            if (attachment.getMinNumberOfOptions() < 1
                    || attachment.getMinNumberOfOptions() > optionsCount) {
                throw new EcNotValidExceptionEc("Invalid min number of options: " + attachment.getJSONObject());
            }

            if (attachment.getMaxNumberOfOptions() < 1
                    || attachment.getMaxNumberOfOptions() < attachment.getMinNumberOfOptions()
                    || attachment.getMaxNumberOfOptions() > optionsCount) {
                throw new EcNotValidExceptionEc("Invalid max number of options: " + attachment.getJSONObject());
            }

            for (int i = 0; i < optionsCount; i++) {
                if (attachment.getPollOptions()[i].length() > Constants.EC_MAX_POLL_OPTION_LENGTH
                        || attachment.getPollOptions()[i].isEmpty()) {
                    throw new EcNotValidExceptionEc("Invalid poll options length: " + attachment.getJSONObject());
                }
            }

            if (attachment.getMinRangeValue() < Constants.EC_MIN_VOTE_VALUE || attachment.getMaxRangeValue() > Constants.EC_MAX_VOTE_VALUE
                    || attachment.getMaxRangeValue() < attachment.getMinRangeValue()) {
                throw new EcNotValidExceptionEc("Invalid range: " + attachment.getJSONObject());
            }

            if (attachment.getFinishHeight() <= attachment.getFinishValidationHeight(transaction) + 1
                    || attachment.getFinishHeight() >= attachment.getFinishValidationHeight(transaction) + Constants.EC_MAX_POLL_DURATION) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid finishing height" + attachment.getJSONObject());
            }

            if (!attachment.getVoteWeighting().acceptsVotes() || attachment.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
                throw new EcNotValidExceptionEc("VotingModel " + attachment.getVoteWeighting().getVotingModel() + " not valid for regular polls");
            }

            attachment.getVoteWeighting().validate();

        }

        @Override
        boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK
                    && isDuplicate(com.inesv.ecchain.kernel.core.Messaging.POLL_CREATION, getName(), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };
    public final static TransactionType VOTE_CASTING = new com.inesv.ecchain.kernel.core.Messaging() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_VOTE_CASTING;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.VOTE_CASTING;
        }

        @Override
        public String getName() {
            return "VoteCasting";
        }

        @Override
        Mortgaged.MessagingVoteCasting parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingVoteCasting(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingVoteCasting parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingVoteCasting(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingVoteCasting attachment = (Mortgaged.MessagingVoteCasting) transaction.getAttachment();
            Vote.addVote(transaction, attachment);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {

            Mortgaged.MessagingVoteCasting attachment = (Mortgaged.MessagingVoteCasting) transaction.getAttachment();
            if (attachment.getPollId() == 0 || attachment.getPollVote() == null
                    || attachment.getPollVote().length > Constants.EC_MAX_POLL_OPTION_COUNT) {
                throw new EcNotValidExceptionEc("Invalid vote casting attachment: " + attachment.getJSONObject());
            }

            long pollId = attachment.getPollId();

            Poll poll = Poll.getPoll(pollId);
            if (poll == null) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid poll: " + Long.toUnsignedString(attachment.getPollId()));
            }

            if (Vote.getVote(pollId, transaction.getSenderId()) != null) {
                throw new EcNotCurrentlyValidExceptionEc("Double voting attempt");
            }

            if (poll.getFinishHeight() <= attachment.getFinishValidationHeight(transaction)) {
                throw new EcNotCurrentlyValidExceptionEc("Voting for this poll finishes at " + poll.getFinishHeight());
            }

            byte[] votes = attachment.getPollVote();
            int positiveCount = 0;
            for (byte vote : votes) {
                if (vote != Constants.EC_NO_VOTE_VALUE && (vote < poll.getMinRangeValue() || vote > poll.getMaxRangeValue())) {
                    throw new EcNotValidExceptionEc(String.format("Invalid vote %d, vote must be between %d and %d",
                            vote, poll.getMinRangeValue(), poll.getMaxRangeValue()));
                }
                if (vote != Constants.EC_NO_VOTE_VALUE) {
                    positiveCount++;
                }
            }

            if (positiveCount < poll.getMinNumberOfOptions() || positiveCount > poll.getMaxNumberOfOptions()) {
                throw new EcNotValidExceptionEc(String.format("Invalid num of choices %d, number of choices must be between %d and %d",
                        positiveCount, poll.getMinNumberOfOptions(), poll.getMaxNumberOfOptions()));
            }
        }

        @Override
        boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MessagingVoteCasting attachment = (Mortgaged.MessagingVoteCasting) transaction.getAttachment();
            String key = Long.toUnsignedString(attachment.getPollId()) + ":" + Long.toUnsignedString(transaction.getSenderId());
            return isDuplicate(com.inesv.ecchain.kernel.core.Messaging.VOTE_CASTING, key, duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return false;
        }

    };
    public static final TransactionType PHASING_VOTE_CASTING = new com.inesv.ecchain.kernel.core.Messaging() {

        private final Fee PHASING_VOTE_FEE = (transaction, appendage) -> {
            Mortgaged.MessagingPhasingVoteCasting attachment = (Mortgaged.MessagingPhasingVoteCasting) transaction.getAttachment();
            return attachment.getTransactionFullHashes().size() * Constants.ONE_EC;
        };

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_PHASING_VOTE_CASTING;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.PHASING_VOTE_CASTING;
        }

        @Override
        public String getName() {
            return "PhasingVoteCasting";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return PHASING_VOTE_FEE;
        }

        @Override
        Mortgaged.MessagingPhasingVoteCasting parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingPhasingVoteCasting(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingPhasingVoteCasting parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingPhasingVoteCasting(attachmentData);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {

            Mortgaged.MessagingPhasingVoteCasting attachment = (Mortgaged.MessagingPhasingVoteCasting) transaction.getAttachment();
            byte[] revealedSecret = attachment.getRevealedSecret();
            if (revealedSecret.length > Constants.EC_MAX_PHASING_REVEALED_SECRET_LENGTH) {
                throw new EcNotValidExceptionEc("Invalid revealed secret length " + revealedSecret.length);
            }
            byte[] hashedSecret = null;
            byte algorithm = 0;

            List<byte[]> hashes = attachment.getTransactionFullHashes();
            if (hashes.size() > Constants.EC_MAX_PHASING_VOTE_TRANSACTIONS) {
                throw new EcNotValidExceptionEc("No more than " + Constants.EC_MAX_PHASING_VOTE_TRANSACTIONS + " votes allowed for two-phased multi-voting");
            }

            long voterId = transaction.getSenderId();
            for (byte[] hash : hashes) {
                long phasedTransactionId = Convert.fullhashtoid(hash);
                if (phasedTransactionId == 0) {
                    throw new EcNotValidExceptionEc("Invalid phased transactionFullHash " + Convert.toHexString(hash));
                }

                PhasingPoll poll = PhasingPoll.getPoll(phasedTransactionId);
                if (poll == null) {
                    throw new EcNotCurrentlyValidExceptionEc("Invalid phased transaction " + Long.toUnsignedString(phasedTransactionId)
                            + ", or phasing is finished");
                }
                if (!poll.getVoteWeighting().acceptsVotes()) {
                    throw new EcNotValidExceptionEc("This phased transaction does not require or accept voting");
                }
                long[] whitelist = poll.getWhitelist();
                if (whitelist.length > 0 && Arrays.binarySearch(whitelist, voterId) < 0) {
                    throw new EcNotValidExceptionEc("Voter is not in the phased transaction whitelist");
                }
                if (revealedSecret.length > 0) {
                    if (poll.getVoteWeighting().getVotingModel() != VoteWeighting.VotingModel.HASH) {
                        throw new EcNotValidExceptionEc("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " does not accept by-hash voting");
                    }
                    if (hashedSecret != null && !Arrays.equals(poll.getHashedSecret(), hashedSecret)) {
                        throw new EcNotValidExceptionEc("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " is using a different hashedSecret");
                    }
                    if (algorithm != 0 && algorithm != poll.getAlgorithm()) {
                        throw new EcNotValidExceptionEc("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " is using a different hashedSecretAlgorithm");
                    }
                    if (hashedSecret == null && !poll.verifySecret(revealedSecret)) {
                        throw new EcNotValidExceptionEc("Revealed secret does not match phased transaction hashed secret");
                    }
                    hashedSecret = poll.getHashedSecret();
                    algorithm = poll.getAlgorithm();
                } else if (poll.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
                    throw new EcNotValidExceptionEc("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " requires revealed secret for approval");
                }
                if (!Arrays.equals(poll.getFullHash(), hash)) {
                    throw new EcNotCurrentlyValidExceptionEc("Phased transaction hash does not match hash in voting transaction");
                }
                if (poll.getFinishHeight() <= attachment.getFinishValidationHeight(transaction) + 1) {
                    throw new EcNotCurrentlyValidExceptionEc(String.format("Phased transaction finishes at height %d which is not after approval transaction height %d",
                            poll.getFinishHeight(), attachment.getFinishValidationHeight(transaction) + 1));
                }
            }
        }

        @Override
        final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingPhasingVoteCasting attachment = (Mortgaged.MessagingPhasingVoteCasting) transaction.getAttachment();
            List<byte[]> hashes = attachment.getTransactionFullHashes();
            for (byte[] hash : hashes) {
                PhasingVote.addVote(transaction, senderAccount, Convert.fullhashtoid(hash));
            }
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };
    public static final TransactionType HUB_ANNOUNCEMENT = new com.inesv.ecchain.kernel.core.Messaging() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.HUB_ANNOUNCEMENT;
        }

        @Override
        public String getName() {
            return "HubAnnouncement";
        }

        @Override
        Mortgaged.MessagingHubAnnouncement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingHubAnnouncement(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingHubAnnouncement parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingHubAnnouncement(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingHubAnnouncement attachment = (Mortgaged.MessagingHubAnnouncement) transaction.getAttachment();
            Heart.addOrUpdateHub(transaction, attachment);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            if (EcBlockchainImpl.getInstance().getHeight() < Constants.EC_TRANSPARENT_FORGING_BLOCK_4) {
                throw new EcNotYetEnabledExceptionEc("Heart terminal announcement not yet enabled at height " + EcBlockchainImpl.getInstance().getHeight());
            }
            Mortgaged.MessagingHubAnnouncement attachment = (Mortgaged.MessagingHubAnnouncement) transaction.getAttachment();
            if (attachment.getMinFeePerByteNQT() < 0 || attachment.getMinFeePerByteNQT() > Constants.EC_MAX_BALANCE_NQT
                    || attachment.getUris().length > Constants.EC_MAX_HUB_ANNOUNCEMENT_URIS) {
                // cfb: "0" is allowed to show that another way to determine the min fee should be used
                throw new EcNotValidExceptionEc("Invalid heart terminal announcement: " + attachment.getJSONObject());
            }
            for (String uri : attachment.getUris()) {
                if (uri.length() > Constants.EC_MAX_HUB_ANNOUNCEMENT_URI_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid URI length: " + uri.length());
                }
                //also check URI validity here?
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };
    public static final com.inesv.ecchain.kernel.core.Messaging ACCOUNT_INFO = new com.inesv.ecchain.kernel.core.Messaging() {

        private final Fee ACCOUNT_INFO_FEE = new Fee.SizeBasedFee(Constants.ONE_EC, 2 * Constants.ONE_EC, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                Mortgaged.MessagingAccountInfo attachment = (Mortgaged.MessagingAccountInfo) transaction.getAttachment();
                return attachment.getName().length() + attachment.getDescription().length();
            }
        };

        @Override
        public byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ACCOUNT_INFO;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ACCOUNT_INFO;
        }

        @Override
        public String getName() {
            return "AccountInfo";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return ACCOUNT_INFO_FEE;
        }

        @Override
        Mortgaged.MessagingAccountInfo parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAccountInfo(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingAccountInfo parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAccountInfo(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MessagingAccountInfo attachment = (Mortgaged.MessagingAccountInfo) transaction.getAttachment();
            if (attachment.getName().length() > Constants.EC_MAX_ACCOUNT_NAME_LENGTH
                    || attachment.getDescription().length() > Constants.EC_MAX_ACCOUNT_DESCRIPTION_LENGTH) {
                throw new EcNotValidExceptionEc("Invalid account info issuance: " + attachment.getJSONObject());
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingAccountInfo attachment = (Mortgaged.MessagingAccountInfo) transaction.getAttachment();
            senderAccount.setAccountInfo(attachment.getName(), attachment.getDescription());
        }

        @Override
        boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK
                    && isDuplicate(com.inesv.ecchain.kernel.core.Messaging.ACCOUNT_INFO, getName(), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

    };
    public static final com.inesv.ecchain.kernel.core.Messaging ACCOUNT_PROPERTY = new com.inesv.ecchain.kernel.core.Messaging() {

        private final Fee ACCOUNT_PROPERTY_FEE = new Fee.SizeBasedFee(Constants.ONE_EC, Constants.ONE_EC, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                Mortgaged.MessagingAccountProperty attachment = (Mortgaged.MessagingAccountProperty) transaction.getAttachment();
                return attachment.getValue().length();
            }
        };

        @Override
        public byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ACCOUNT_PROPERTY;
        }

        @Override
        public String getName() {
            return "AccountProperty";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return ACCOUNT_PROPERTY_FEE;
        }

        @Override
        Mortgaged.MessagingAccountProperty parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAccountProperty(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingAccountProperty parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAccountProperty(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MessagingAccountProperty attachment = (Mortgaged.MessagingAccountProperty) transaction.getAttachment();
            if (attachment.getProperty().length() > Constants.EC_MAX_ACCOUNT_PROPERTY_NAME_LENGTH
                    || attachment.getProperty().length() == 0
                    || attachment.getValue().length() > Constants.EC_MAX_ACCOUNT_PROPERTY_VALUE_LENGTH) {
                throw new EcNotValidExceptionEc("Invalid account property: " + attachment.getJSONObject());
            }
            if (transaction.getAmountNQT() != 0) {
                throw new EcNotValidExceptionEc("Account property transaction cannot be used to send EC");
            }
            if (transaction.getRecipientId() == Genesis.EC_CREATOR_ID) {
                throw new EcNotValidExceptionEc("Setting Genesis account properties not allowed");
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingAccountProperty attachment = (Mortgaged.MessagingAccountProperty) transaction.getAttachment();
            recipientAccount.setProperty(transaction, senderAccount, attachment.getProperty(), attachment.getValue());
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
    public static final com.inesv.ecchain.kernel.core.Messaging ACCOUNT_PROPERTY_DELETE = new com.inesv.ecchain.kernel.core.Messaging() {

        @Override
        public byte getSubtype() {
            return Constants.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ACCOUNT_PROPERTY_DELETE;
        }

        @Override
        public String getName() {
            return "AccountPropertyDelete";
        }

        @Override
        Mortgaged.MessagingAccountPropertyDelete parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAccountPropertyDelete(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MessagingAccountPropertyDelete parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MessagingAccountPropertyDelete(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MessagingAccountPropertyDelete attachment = (Mortgaged.MessagingAccountPropertyDelete) transaction.getAttachment();
            Account.AccountProperty accountProperty = Account.getProperty(attachment.getPropertyId());
            if (accountProperty == null) {
                throw new EcNotCurrentlyValidExceptionEc("No such property " + Long.toUnsignedString(attachment.getPropertyId()));
            }
            if (accountProperty.getRecipientId() != transaction.getSenderId() && accountProperty.getSetterId() != transaction.getSenderId()) {
                throw new EcNotValidExceptionEc("Account " + Long.toUnsignedString(transaction.getSenderId())
                        + " cannot delete property " + Long.toUnsignedString(attachment.getPropertyId()));
            }
            if (accountProperty.getRecipientId() != transaction.getRecipientId()) {
                throw new EcNotValidExceptionEc("Account property " + Long.toUnsignedString(attachment.getPropertyId())
                        + " does not belong to " + Long.toUnsignedString(transaction.getRecipientId()));
            }
            if (transaction.getAmountNQT() != 0) {
                throw new EcNotValidExceptionEc("Account property transaction cannot be used to send EC");
            }
            if (transaction.getRecipientId() == Genesis.EC_CREATOR_ID) {
                throw new EcNotValidExceptionEc("Deleting Genesis account properties not allowed");
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MessagingAccountPropertyDelete attachment = (Mortgaged.MessagingAccountPropertyDelete) transaction.getAttachment();
            senderAccount.deleteProperty(attachment.getPropertyId());
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

    private Messaging() {
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_MESSAGING;
    }

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
    }

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

}
