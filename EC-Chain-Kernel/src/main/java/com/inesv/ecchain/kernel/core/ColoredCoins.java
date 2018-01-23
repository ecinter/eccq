package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;

public abstract class ColoredCoins extends TransactionType {

    public static final TransactionType ASSET_ISSUANCE = new com.inesv.ecchain.kernel.core.ColoredCoins() {

        private final Fee SINGLETON_ASSET_FEE = new Fee.SizeBasedFee(Constants.ONE_EC, Constants.ONE_EC, 32) {
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                Mortgaged.ColoredCoinsAssetIssuance attachment = (Mortgaged.ColoredCoinsAssetIssuance) transaction.getAttachment();
                return attachment.getDescription().length();
            }
        };

        private final Fee ASSET_ISSUANCE_FEE = (transaction, appendage) -> isSingletonIssuance(transaction) ?
                SINGLETON_ASSET_FEE.getFee(transaction, appendage) : 1000 * Constants.ONE_EC;

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_ASSET_ISSUANCE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_ISSUANCE;
        }

        @Override
        public String getName() {
            return "AssetIssuance";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return ASSET_ISSUANCE_FEE;
        }

        @Override
        long[] getBackFees(Transaction transaction) {
            if (isSingletonIssuance(transaction)) {
                return Convert.EC_EMPTY_LONG;
            }
            long feeNQT = transaction.getFeeNQT();
            return new long[]{feeNQT * 3 / 10, feeNQT * 2 / 10, feeNQT / 10};
        }

        @Override
        Mortgaged.ColoredCoinsAssetIssuance parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAssetIssuance(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsAssetIssuance parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAssetIssuance(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsAssetIssuance attachment = (Mortgaged.ColoredCoinsAssetIssuance) transaction.getAttachment();
            long assetId = transaction.getTransactionId();
            Property.addProperty(transaction, attachment);
            senderAccount.addToAssetAndUnconfirmedAssetBalanceQNT(getLedgerEvent(), assetId, assetId, attachment.getQuantityQNT());
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ColoredCoinsAssetIssuance attachment = (Mortgaged.ColoredCoinsAssetIssuance) transaction.getAttachment();
            if (attachment.getName().length() < Constants.EC_MIN_ASSET_NAME_LENGTH
                    || attachment.getName().length() > Constants.EC_MAX_ASSET_NAME_LENGTH
                    || attachment.getDescription().length() > Constants.EC_MAX_ASSET_DESCRIPTION_LENGTH
                    || attachment.getDecimals() < 0 || attachment.getDecimals() > 8
                    || attachment.getQuantityQNT() <= 0
                    || attachment.getQuantityQNT() > Constants.EC_MAX_ASSET_QUANTITY_QNT
                    ) {
                throw new EcNotValidExceptionEc("Invalid asset issuance: " + attachment.getJSONObject());
            }
            String normalizedName = attachment.getName().toLowerCase();
            for (int i = 0; i < normalizedName.length(); i++) {
                if (Constants.EC_ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
                    throw new EcNotValidExceptionEc("Invalid asset name: " + normalizedName);
                }
            }
        }

        @Override
        boolean isBlockDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
            return EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK
                    && !isSingletonIssuance(transaction)
                    && isDuplicate(com.inesv.ecchain.kernel.core.ColoredCoins.ASSET_ISSUANCE, getName(), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

        @Override
        public boolean isPhasingSafe() {
            return true;
        }

        private boolean isSingletonIssuance(Transaction transaction) {
            Mortgaged.ColoredCoinsAssetIssuance attachment = (Mortgaged.ColoredCoinsAssetIssuance) transaction.getAttachment();
            return attachment.getQuantityQNT() == 1 && attachment.getDecimals() == 0
                    && attachment.getDescription().length() <= Constants.EC_MAX_SINGLETON_ASSET_DESCRIPTION_LENGTH;
        }

    };
    public static final TransactionType ASSET_TRANSFER = new com.inesv.ecchain.kernel.core.ColoredCoins() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_ASSET_TRANSFER;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_TRANSFER;
        }

        @Override
        public String getName() {
            return "PropertyTransfer";
        }

        @Override
        Mortgaged.ColoredCoinsAssetTransfer parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAssetTransfer(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsAssetTransfer parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAssetTransfer(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsAssetTransfer attachment = (Mortgaged.ColoredCoinsAssetTransfer) transaction.getAttachment();
            long unconfirmedAssetBalance = senderAccount.getUnconfirmedPropertyBalanceQNT(attachment.getAssetId());
            if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getAssetId(), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsAssetTransfer attachment = (Mortgaged.ColoredCoinsAssetTransfer) transaction.getAttachment();
            senderAccount.addToAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(), attachment.getAssetId(),
                    -attachment.getQuantityQNT());
            if (recipientAccount.getId() == Genesis.EC_CREATOR_ID) {
                Property.deleteProperty(transaction, attachment.getAssetId(), attachment.getQuantityQNT());
            } else {
                recipientAccount.addToAssetAndUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getAssetId(), attachment.getQuantityQNT());
                PropertyTransfer.addAssetTransfer(transaction, attachment);
            }
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsAssetTransfer attachment = (Mortgaged.ColoredCoinsAssetTransfer) transaction.getAttachment();
            senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                    attachment.getAssetId(), attachment.getQuantityQNT());
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ColoredCoinsAssetTransfer attachment = (Mortgaged.ColoredCoinsAssetTransfer) transaction.getAttachment();
            if (transaction.getAmountNQT() != 0
                    || attachment.getComment() != null && attachment.getComment().length() > Constants.EC_MAX_ASSET_TRANSFER_COMMENT_LENGTH
                    || attachment.getAssetId() == 0) {
                throw new EcNotValidExceptionEc("Invalid property transfer amount or comment: " + attachment.getJSONObject());
            }
            if (transaction.getRecipientId() == Genesis.EC_CREATOR_ID && attachment.getFinishValidationHeight(transaction) > Constants.EC_SHUFFLING_BLOCK) {
                throw new EcNotValidExceptionEc("Property transfer to Genesis no longer allowed, "
                        + "use property delete attachment instead");
            }
            if (transaction.getVersion() > 0 && attachment.getComment() != null) {
                throw new EcNotValidExceptionEc("Property transfer comments no longer allowed, use message " +
                        "or encrypted message appendix instead");
            }
            Property property = Property.getAsset(attachment.getAssetId());
            if (attachment.getQuantityQNT() <= 0 || (property != null && attachment.getQuantityQNT() > property.getInitialQuantityQNT())) {
                throw new EcNotValidExceptionEc("Invalid property transfer property or quantity: " + attachment.getJSONObject());
            }
            if (property == null) {
                throw new EcNotCurrentlyValidExceptionEc("Property " + Long.toUnsignedString(attachment.getAssetId()) +
                        " does not exist yet");
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
    public static final TransactionType ASSET_DELETE = new com.inesv.ecchain.kernel.core.ColoredCoins() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_ASSET_DELETE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_DELETE;
        }

        @Override
        public String getName() {
            return "PropertyDelete";
        }

        @Override
        Mortgaged.ColoredCoinsAssetDelete parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAssetDelete(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsAssetDelete parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAssetDelete(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsAssetDelete attachment = (Mortgaged.ColoredCoinsAssetDelete) transaction.getAttachment();
            long unconfirmedAssetBalance = senderAccount.getUnconfirmedPropertyBalanceQNT(attachment.getAssetId());
            if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getAssetId(), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsAssetDelete attachment = (Mortgaged.ColoredCoinsAssetDelete) transaction.getAttachment();
            senderAccount.addToAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(), attachment.getAssetId(),
                    -attachment.getQuantityQNT());
            Property.deleteProperty(transaction, attachment.getAssetId(), attachment.getQuantityQNT());
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsAssetDelete attachment = (Mortgaged.ColoredCoinsAssetDelete) transaction.getAttachment();
            senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                    attachment.getAssetId(), attachment.getQuantityQNT());
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ColoredCoinsAssetDelete attachment = (Mortgaged.ColoredCoinsAssetDelete) transaction.getAttachment();
            if (attachment.getAssetId() == 0) {
                throw new EcNotValidExceptionEc("Invalid property identifier: " + attachment.getJSONObject());
            }
            Property property = Property.getAsset(attachment.getAssetId());
            if (attachment.getQuantityQNT() <= 0 || (property != null && attachment.getQuantityQNT() > property.getInitialQuantityQNT())) {
                throw new EcNotValidExceptionEc("Invalid property delete property or quantity: " + attachment.getJSONObject());
            }
            if (property == null) {
                throw new EcNotCurrentlyValidExceptionEc("Property " + Long.toUnsignedString(attachment.getAssetId()) +
                        " does not exist yet");
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
    public static final TransactionType ASK_ORDER_PLACEMENT = new ColoredCoinsOrderPlacement() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_ASK_ORDER_PLACEMENT;
        }

        @Override
        public String getName() {
            return "AskOrderPlacement";
        }

        @Override
        Mortgaged.ColoredCoinsAskOrderPlacement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAskOrderPlacement(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsAskOrderPlacement parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAskOrderPlacement(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsAskOrderPlacement attachment = (Mortgaged.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
            long unconfirmedAssetBalance = senderAccount.getUnconfirmedPropertyBalanceQNT(attachment.getAssetId());
            if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getAssetId(), -attachment.getQuantityQNT());
                return true;
            }
            return false;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsAskOrderPlacement attachment = (Mortgaged.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
            Order.Ask.addOrder(transaction, attachment);
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsAskOrderPlacement attachment = (Mortgaged.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
            senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                    attachment.getAssetId(), attachment.getQuantityQNT());
        }

    };
    public final static TransactionType BID_ORDER_PLACEMENT = new ColoredCoinsOrderPlacement() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_BID_ORDER_PLACEMENT;
        }

        @Override
        public String getName() {
            return "BidOrderPlacement";
        }

        @Override
        Mortgaged.ColoredCoinsBidOrderPlacement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsBidOrderPlacement(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsBidOrderPlacement parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsBidOrderPlacement(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsBidOrderPlacement attachment = (Mortgaged.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
            if (senderAccount.getUnconfirmedBalanceNQT() >= Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceNQT())) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                        -Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceNQT()));
                return true;
            }
            return false;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsBidOrderPlacement attachment = (Mortgaged.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
            Order.Bid.addOrder(transaction, attachment);
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsBidOrderPlacement attachment = (Mortgaged.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                    Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceNQT()));
        }

    };
    public static final TransactionType ASK_ORDER_CANCELLATION = new ColoredCoinsOrderCancellation() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_ASK_ORDER_CANCELLATION;
        }

        @Override
        public String getName() {
            return "AskOrderCancellation";
        }

        @Override
        Mortgaged.ColoredCoinsAskOrderCancellation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAskOrderCancellation(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsAskOrderCancellation parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsAskOrderCancellation(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsAskOrderCancellation attachment = (Mortgaged.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
            Order order = Order.Ask.getAskOrder(attachment.getOrderId());
            Order.Ask.removeOrder(attachment.getOrderId());
            if (order != null) {
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getTransactionId(),
                        order.getAssetId(), order.getQuantityQNT());
            }
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ColoredCoinsAskOrderCancellation attachment = (Mortgaged.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
            Order ask = Order.Ask.getAskOrder(attachment.getOrderId());
            if (ask == null) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid ask order: " + Long.toUnsignedString(attachment.getOrderId()));
            }
            if (ask.getAccountId() != transaction.getSenderId()) {
                throw new EcNotValidExceptionEc("Order " + Long.toUnsignedString(attachment.getOrderId()) + " was created by account "
                        + Long.toUnsignedString(ask.getAccountId()));
            }
        }

    };
    public static final TransactionType BID_ORDER_CANCELLATION = new ColoredCoinsOrderCancellation() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_BID_ORDER_CANCELLATION;
        }

        @Override
        public String getName() {
            return "BidOrderCancellation";
        }

        @Override
        Mortgaged.ColoredCoinsBidOrderCancellation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsBidOrderCancellation(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsBidOrderCancellation parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.ColoredCoinsBidOrderCancellation(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsBidOrderCancellation attachment = (Mortgaged.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
            Order order = Order.Bid.getBidOrder(attachment.getOrderId());
            Order.Bid.removeOrder(attachment.getOrderId());
            if (order != null) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                        Math.multiplyExact(order.getQuantityQNT(), order.getPriceNQT()));
            }
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ColoredCoinsBidOrderCancellation attachment = (Mortgaged.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
            Order bid = Order.Bid.getBidOrder(attachment.getOrderId());
            if (bid == null) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid bid order: " + Long.toUnsignedString(attachment.getOrderId()));
            }
            if (bid.getAccountId() != transaction.getSenderId()) {
                throw new EcNotValidExceptionEc("Order " + Long.toUnsignedString(attachment.getOrderId()) + " was created by account "
                        + Long.toUnsignedString(bid.getAccountId()));
            }
        }

    };
    public static final TransactionType DIVIDEND_PAYMENT = new com.inesv.ecchain.kernel.core.ColoredCoins() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_COLORED_COINS_DIVIDEND_PAYMENT;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.ASSET_DIVIDEND_PAYMENT;
        }

        @Override
        public String getName() {
            return "DividendPayment";
        }

        @Override
        Mortgaged.ColoredCoinsDividendPayment parseAttachment(ByteBuffer buffer, byte transactionVersion) {
            return new Mortgaged.ColoredCoinsDividendPayment(buffer, transactionVersion);
        }

        @Override
        Mortgaged.ColoredCoinsDividendPayment parseAttachment(JSONObject attachmentData) {
            return new Mortgaged.ColoredCoinsDividendPayment(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsDividendPayment attachment = (Mortgaged.ColoredCoinsDividendPayment) transaction.getAttachment();
            long assetId = attachment.getAssetId();
            Property property = Property.getAsset(assetId, attachment.getHeight());
            if (property == null) {
                return true;
            }
            long quantityQNT = property.getQuantityQNT() - senderAccount.getPropertyBalanceQNT(assetId, attachment.getHeight());
            long totalDividendPayment = Math.multiplyExact(attachment.getAmountNQTPerQNT(), quantityQNT);
            if (senderAccount.getUnconfirmedBalanceNQT() >= totalDividendPayment) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), -totalDividendPayment);
                return true;
            }
            return false;
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.ColoredCoinsDividendPayment attachment = (Mortgaged.ColoredCoinsDividendPayment) transaction.getAttachment();
            senderAccount.payDividends(transaction.getTransactionId(), attachment);
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.ColoredCoinsDividendPayment attachment = (Mortgaged.ColoredCoinsDividendPayment) transaction.getAttachment();
            long assetId = attachment.getAssetId();
            Property property = Property.getAsset(assetId, attachment.getHeight());
            if (property == null) {
                return;
            }
            long quantityQNT = property.getQuantityQNT() - senderAccount.getPropertyBalanceQNT(assetId, attachment.getHeight());
            long totalDividendPayment = Math.multiplyExact(attachment.getAmountNQTPerQNT(), quantityQNT);
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), totalDividendPayment);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ColoredCoinsDividendPayment attachment = (Mortgaged.ColoredCoinsDividendPayment) transaction.getAttachment();
            if (attachment.getHeight() > EcBlockchainImpl.getInstance().getHeight()) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid dividend payment height: " + attachment.getHeight()
                        + ", must not exceed current EC_BLOCKCHAIN height " + EcBlockchainImpl.getInstance().getHeight());
            }
            if (attachment.getHeight() <= attachment.getFinishValidationHeight(transaction) - Constants.EC_MAX_DIVIDEND_PAYMENT_ROLLBACK) {
                throw new EcNotCurrentlyValidExceptionEc("Invalid dividend payment height: " + attachment.getHeight()
                        + ", must be less than " + Constants.EC_MAX_DIVIDEND_PAYMENT_ROLLBACK
                        + " blocks before " + attachment.getFinishValidationHeight(transaction));
            }
            Property property;
            if (EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK) {
                property = Property.getAsset(attachment.getAssetId(), attachment.getHeight());
            } else {
                property = Property.getAsset(attachment.getAssetId());
            }
            if (property == null) {
                throw new EcNotCurrentlyValidExceptionEc("Property " + Long.toUnsignedString(attachment.getAssetId())
                        + " for dividend payment doesn't exist yet");
            }
            if (property.getAccountId() != transaction.getSenderId() || attachment.getAmountNQTPerQNT() <= 0) {
                throw new EcNotValidExceptionEc("Invalid dividend payment sender or amount " + attachment.getJSONObject());
            }
            if (EcBlockchainImpl.getInstance().getHeight() > Constants.EC_FXT_BLOCK) {
                PropertyDividend lastDividend = PropertyDividend.getLastDividend(attachment.getAssetId());
                if (lastDividend != null && lastDividend.getHeight() > EcBlockchainImpl.getInstance().getHeight() - 60) {
                    throw new EcNotCurrentlyValidExceptionEc("Last dividend payment for property " + Long.toUnsignedString(attachment.getAssetId())
                            + " was less than 60 blocks ago at " + lastDividend.getHeight() + ", current height is " + EcBlockchainImpl.getInstance().getHeight()
                            + ", limit is one dividend per 60 blocks");
                }
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ColoredCoinsDividendPayment attachment = (Mortgaged.ColoredCoinsDividendPayment) transaction.getAttachment();
            return EcBlockchainImpl.getInstance().getHeight() > Constants.EC_FXT_BLOCK &&
                    isDuplicate(com.inesv.ecchain.kernel.core.ColoredCoins.DIVIDEND_PAYMENT, Long.toUnsignedString(attachment.getAssetId()), duplicates, true);
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

    private ColoredCoins() {
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_COLORED_COINS;
    }

    abstract static class ColoredCoinsOrderPlacement extends com.inesv.ecchain.kernel.core.ColoredCoins {

        @Override
        final void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.ColoredCoinsOrderPlacement attachment = (Mortgaged.ColoredCoinsOrderPlacement) transaction.getAttachment();
            if (attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.EC_MAX_BALANCE_NQT
                    || attachment.getAssetId() == 0) {
                throw new EcNotValidExceptionEc("Invalid property order placement: " + attachment.getJSONObject());
            }
            Property property = Property.getAsset(attachment.getAssetId());
            if (attachment.getQuantityQNT() <= 0 || (property != null && attachment.getQuantityQNT() > property.getInitialQuantityQNT())) {
                throw new EcNotValidExceptionEc("Invalid property order placement property or quantity: " + attachment.getJSONObject());
            }
            if (property == null) {
                throw new EcNotCurrentlyValidExceptionEc("Property " + Long.toUnsignedString(attachment.getAssetId()) +
                        " does not exist yet");
            }
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }

    }

    abstract static class ColoredCoinsOrderCancellation extends com.inesv.ecchain.kernel.core.ColoredCoins {

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.ColoredCoinsOrderCancellation attachment = (Mortgaged.ColoredCoinsOrderCancellation) transaction.getAttachment();
            return TransactionType.isDuplicate(com.inesv.ecchain.kernel.core.ColoredCoins.ASK_ORDER_CANCELLATION, Long.toUnsignedString(attachment.getOrderId()), duplicates, true);
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }

    }

}
