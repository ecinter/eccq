package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public abstract class TransactionType {

    public static TransactionType selectTransactionType(byte type, byte subtype) {
        switch (type) {
            case Constants.TYPE_PAYMENT:
                switch (subtype) {
                    case Constants.SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return Payment.ORDINARY;
                    default:
                        return null;
                }
            case Constants.TYPE_MESSAGING:
                switch (subtype) {
                    case Constants.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE:
                        return Messaging.ARBITRARY_MESSAGE;
                    case Constants.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                        return Messaging.ALIAS_ASSIGNMENT;
                    case Constants.SUBTYPE_MESSAGING_POLL_CREATION:
                        return Messaging.POLL_CREATION;
                    case Constants.SUBTYPE_MESSAGING_VOTE_CASTING:
                        return Messaging.VOTE_CASTING;
                    case Constants.SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT:
                        return Messaging.HUB_ANNOUNCEMENT;
                    case Constants.SUBTYPE_MESSAGING_ACCOUNT_INFO:
                        return Messaging.ACCOUNT_INFO;
                    case Constants.SUBTYPE_MESSAGING_ALIAS_SELL:
                        return Messaging.ALIAS_SELL;
                    case Constants.SUBTYPE_MESSAGING_ALIAS_BUY:
                        return Messaging.ALIAS_BUY;
                    case Constants.SUBTYPE_MESSAGING_ALIAS_DELETE:
                        return Messaging.ALIAS_DELETE;
                    case Constants.SUBTYPE_MESSAGING_PHASING_VOTE_CASTING:
                        return Messaging.PHASING_VOTE_CASTING;
                    case Constants.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY:
                        return Messaging.ACCOUNT_PROPERTY;
                    case Constants.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE:
                        return Messaging.ACCOUNT_PROPERTY_DELETE;
                    default:
                        return null;
                }
            case Constants.TYPE_COLORED_COINS:
                switch (subtype) {
                    case Constants.SUBTYPE_COLORED_COINS_ASSET_ISSUANCE:
                        return ColoredCoins.ASSET_ISSUANCE;
                    case Constants.SUBTYPE_COLORED_COINS_ASSET_TRANSFER:
                        return ColoredCoins.ASSET_TRANSFER;
                    case Constants.SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT:
                        return ColoredCoins.ASK_ORDER_PLACEMENT;
                    case Constants.SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT:
                        return ColoredCoins.BID_ORDER_PLACEMENT;
                    case Constants.SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION:
                        return ColoredCoins.ASK_ORDER_CANCELLATION;
                    case Constants.SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION:
                        return ColoredCoins.BID_ORDER_CANCELLATION;
                    case Constants.SUBTYPE_COLORED_COINS_DIVIDEND_PAYMENT:
                        return ColoredCoins.DIVIDEND_PAYMENT;
                    case Constants.SUBTYPE_COLORED_COINS_ASSET_DELETE:
                        return ColoredCoins.ASSET_DELETE;
                    default:
                        return null;
                }
            case Constants.TYPE_DIGITAL_GOODS:
                switch (subtype) {
                    case Constants.SUBTYPE_DIGITAL_GOODS_LISTING:
                        return DigitalGoods.LISTING;
                    case Constants.SUBTYPE_DIGITAL_GOODS_DELISTING:
                        return DigitalGoods.DELISTING;
                    case Constants.SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE:
                        return DigitalGoods.PRICE_CHANGE;
                    case Constants.SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE:
                        return DigitalGoods.QUANTITY_CHANGE;
                    case Constants.SUBTYPE_DIGITAL_GOODS_PURCHASE:
                        return DigitalGoods.PURCHASE;
                    case Constants.SUBTYPE_DIGITAL_GOODS_DELIVERY:
                        return DigitalGoods.DELIVERY;
                    case Constants.SUBTYPE_DIGITAL_GOODS_FEEDBACK:
                        return DigitalGoods.FEEDBACK;
                    case Constants.SUBTYPE_DIGITAL_GOODS_REFUND:
                        return DigitalGoods.REFUND;
                    default:
                        return null;
                }
            case Constants.TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case Constants.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return AccountControl.EFFECTIVE_BALANCE_LEASING;
                    case Constants.SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY:
                        return AccountControl.SET_PHASING_ONLY;
                    default:
                        return null;
                }
            case Constants.TYPE_MONETARY_SYSTEM:
                return Coinage.findTransactionType(subtype);
            case Constants.TYPE_DATA:
                switch (subtype) {
                    case Constants.SUBTYPE_DATA_TAGGED_DATA_UPLOAD:
                        return Data.TAGGED_DATA_UPLOAD;
                    case Constants.SUBTYPE_DATA_TAGGED_DATA_EXTEND:
                        return Data.TAGGED_DATA_EXTEND;
                    default:
                        return null;
                }
            case Constants.TYPE_SHUFFLING:
                return ShufflingTransaction.findTransactionType(subtype);
            default:
                return null;
        }
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, boolean exclusive) {
        return isDuplicate(uniqueType, key, duplicates, exclusive ? 0 : Integer.MAX_VALUE);
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, int maxCount) {
        Map<String, Integer> typeDuplicates = duplicates.get(uniqueType);
        if (typeDuplicates == null) {
            typeDuplicates = new HashMap<>();
            duplicates.put(uniqueType, typeDuplicates);
        }
        Integer currentCount = typeDuplicates.get(key);
        if (currentCount == null) {
            typeDuplicates.put(key, maxCount > 0 ? 1 : 0);
            return false;
        }
        if (currentCount == 0) {
            return true;
        }
        if (currentCount < maxCount) {
            typeDuplicates.put(key, currentCount + 1);
            return false;
        }
        return true;
    }

    public abstract byte getType();

    public abstract byte getSubtype();

    public abstract LedgerEvent getLedgerEvent();

    abstract Mortgaged.AbstractMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc;

    abstract Mortgaged.AbstractMortgaged parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc;

    abstract void validateAttachment(Transaction transaction) throws EcValidationException;

    final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        long amountNQT = transaction.getAmountNQT();
        long feeNQT = transaction.getFeeNQT();
        if (transaction.referencedTransactionFullHash() != null
                && transaction.getTimestamp() > Constants.EC_REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP) {
            feeNQT = Math.addExact(feeNQT, Constants.EC_UNCONFIRMED_POOL_DEPOSIT_NQT);
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        if (senderAccount.getUnconfirmedBalanceNQT() < totalAmountNQT
                && !(transaction.getTimestamp() == 0 && Arrays.equals(transaction.getSenderPublicKey(), Genesis.EC_CREATOR_PUBLIC_KEY))) {
            return false;
        }
        senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), -amountNQT, -feeNQT);
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), amountNQT, feeNQT);
            return false;
        }
        return true;
    }

    abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        long amount = transaction.getAmountNQT();
        long transactionId = transaction.getTransactionId();
        if (!transaction.attachmentIsPhased()) {
            senderAccount.addToBalanceNQT(getLedgerEvent(), transactionId, -amount, -transaction.getFeeNQT());
        } else {
            senderAccount.addToBalanceNQT(getLedgerEvent(), transactionId, -amount);
        }
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalanceNQT(getLedgerEvent(), transactionId, amount);
        }
        applyAttachment(transaction, senderAccount, recipientAccount);
    }

    abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount);

    final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                transaction.getAmountNQT(), transaction.getFeeNQT());
        if (transaction.referencedTransactionFullHash() != null
                && transaction.getTimestamp() > Constants.EC_REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP) {
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), 0,
                    Constants.EC_UNCONFIRMED_POOL_DEPOSIT_NQT);
        }
    }

    abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    boolean isPruned(long transactionId) {
        return false;
    }

    public abstract boolean canHaveRecipient();

    public boolean mustHaveRecipient() {
        return canHaveRecipient();
    }

    public abstract boolean isPhasingSafe();

    public boolean isPhasable() {
        return true;
    }

    Fee getBaselineFee(Transaction transaction) {
        return Fee.DEFAULT_FEE;
    }

    Fee getNextFee(Transaction transaction) {
        return getBaselineFee(transaction);
    }

    int getBaselineFeeHeight() {
        return Constants.EC_SHUFFLING_BLOCK;
    }

    int getNextFeeHeight() {
        return Integer.MAX_VALUE;
    }

    long[] getBackFees(Transaction transaction) {
        return Convert.EC_EMPTY_LONG;
    }

    public abstract String getName();

    @Override
    public final String toString() {
        return getName() + " type: " + getType() + ", subtype: " + getSubtype();
    }

}
