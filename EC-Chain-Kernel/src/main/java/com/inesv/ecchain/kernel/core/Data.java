package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.EcTime;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class Data extends TransactionType {

    public static final TransactionType TAGGED_DATA_UPLOAD = new com.inesv.ecchain.kernel.core.Data() {

        @Override
        public byte getSubtype() {
            return Constants.SUBTYPE_DATA_TAGGED_DATA_UPLOAD;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.TAGGED_DATA_UPLOAD;
        }

        @Override
        Mortgaged.TaggedDataUpload parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.TaggedDataUpload(buffer, transactionVersion);
        }

        @Override
        Mortgaged.TaggedDataUpload parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.TaggedDataUpload(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.TaggedDataUpload attachment = (Mortgaged.TaggedDataUpload) transaction.getAttachment();
            if (attachment.getData() == null && new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MIN_PRUNABLE_LIFETIME) {
                throw new EcNotCurrentlyValidExceptionEc("Data has been pruned prematurely");
            }
            if (attachment.getData() != null) {
                if (attachment.getName().length() == 0 || attachment.getName().length() > Constants.EC_MAX_TAGGED_DATA_NAME_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid name length: " + attachment.getName().length());
                }
                if (attachment.getDescription().length() > Constants.EC_MAX_TAGGED_DATA_DESCRIPTION_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid description length: " + attachment.getDescription().length());
                }
                if (attachment.getTags().length() > Constants.EC_MAX_TAGGED_DATA_TAGS_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid tags length: " + attachment.getTags().length());
                }
                if (attachment.getType().length() > Constants.EC_MAX_TAGGED_DATA_TYPE_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid type length: " + attachment.getType().length());
                }
                if (attachment.getChannel().length() > Constants.EC_MAX_TAGGED_DATA_CHANNEL_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid channel length: " + attachment.getChannel().length());
                }
                if (attachment.getFilename().length() > Constants.EC_MAX_TAGGED_DATA_FILENAME_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid filename length: " + attachment.getFilename().length());
                }
                if (attachment.getData().length == 0 || attachment.getData().length > Constants.EC_MAX_TAGGED_DATA_DATA_LENGTH) {
                    throw new EcNotValidExceptionEc("Invalid data length: " + attachment.getData().length);
                }
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.TaggedDataUpload attachment = (Mortgaged.TaggedDataUpload) transaction.getAttachment();
            BadgeData.addBadgeData((TransactionImpl) transaction, attachment);
        }

        @Override
        public String getName() {
            return "TaggedDataUpload";
        }

        @Override
        boolean isPruned(long transactionId) {
            return BadgeData.isPruned(transactionId);
        }

    };
    public static final TransactionType TAGGED_DATA_EXTEND = new com.inesv.ecchain.kernel.core.Data() {

        @Override
        public byte getSubtype() {
            return Constants.SUBTYPE_DATA_TAGGED_DATA_EXTEND;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.TAGGED_DATA_EXTEND;
        }

        @Override
        Mortgaged.TaggedDataExtend parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.TaggedDataExtend(buffer, transactionVersion);
        }

        @Override
        Mortgaged.TaggedDataExtend parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.TaggedDataExtend(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.TaggedDataExtend attachment = (Mortgaged.TaggedDataExtend) transaction.getAttachment();
            if ((attachment.jsonIsPruned() || attachment.getData() == null) && new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MIN_PRUNABLE_LIFETIME) {
                throw new EcNotCurrentlyValidExceptionEc("Data has been pruned prematurely");
            }
            TransactionImpl uploadTransaction = TransactionH2.selectTransaction(attachment.getTaggedDataId(), EcBlockchainImpl.getInstance().getHeight());
            if (uploadTransaction == null) {
                throw new EcNotCurrentlyValidExceptionEc("No such tagged data upload " + Long.toUnsignedString(attachment.getTaggedDataId()));
            }
            if (uploadTransaction.getTransactionType() != TAGGED_DATA_UPLOAD) {
                throw new EcNotValidExceptionEc("Transaction " + Long.toUnsignedString(attachment.getTaggedDataId())
                        + " is not a tagged data upload");
            }
            if (attachment.getData() != null) {
                Mortgaged.TaggedDataUpload taggedDataUpload = (Mortgaged.TaggedDataUpload) uploadTransaction.getAttachment();
                if (!Arrays.equals(attachment.getHash(), taggedDataUpload.getHash())) {
                    throw new EcNotValidExceptionEc("Hashes don't match! Extend hash: " + Convert.toHexString(attachment.getHash())
                            + " upload hash: " + Convert.toHexString(taggedDataUpload.getHash()));
                }
            }
            BadgeData badgeData = BadgeData.getData(attachment.getTaggedDataId());
            if (badgeData != null && badgeData.getTransactionTimestamp() > new EcTime.EpochEcTime().getTime() + 6 * Constants.EC_MIN_PRUNABLE_LIFETIME) {
                throw new EcNotCurrentlyValidExceptionEc("Data already extended, timestamp is " + badgeData.getTransactionTimestamp());
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.TaggedDataExtend attachment = (Mortgaged.TaggedDataExtend) transaction.getAttachment();
            BadgeData.extend(transaction, attachment);
        }

        @Override
        public String getName() {
            return "TaggedDataExtend";
        }

        @Override
        boolean isPruned(long transactionId) {
            return false;
        }

    };
    private static final Fee TAGGED_DATA_FEE = new Fee.SizeBasedFee(Constants.ONE_EC, Constants.ONE_EC / 10) {
        @Override
        public int getSize(TransactionImpl transaction, Enclosure enclosure) {
            return enclosure.getFullSize();
        }
    };

    private Data() {
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_DATA;
    }

    @Override
    final Fee getBaselineFee(Transaction transaction) {
        return TAGGED_DATA_FEE;
    }

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
    }

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    @Override
    public final boolean canHaveRecipient() {
        return false;
    }

    @Override
    public final boolean isPhasingSafe() {
        return false;
    }

    @Override
    public final boolean isPhasable() {
        return false;
    }

}
