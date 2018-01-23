package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;

public abstract class DigitalGoods extends TransactionType {

    public static final TransactionType LISTING = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        private final Fee DGS_LISTING_FEE = new Fee.SizeBasedFee(2 * Constants.ONE_EC, 2 * Constants.ONE_EC, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                Mortgaged.DigitalGoodsListing attachment = (Mortgaged.DigitalGoodsListing) transaction.getAttachment();
                return attachment.getName().length() + attachment.getDescription().length();
            }
        };

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_LISTING;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_LISTING;
        }

        @Override
        public String getName() {
            return "DigitalGoodsListing";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return DGS_LISTING_FEE;
        }

        @Override
        Mortgaged.DigitalGoodsListing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsListing(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsListing parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsListing(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsListing attachment = (Mortgaged.DigitalGoodsListing) transaction.getAttachment();
            ElectronicProductStore.listGoods(transaction, attachment);
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsListing attachment = (Mortgaged.DigitalGoodsListing) transaction.getAttachment();
            if (attachment.getName().length() == 0
                    || attachment.getName().length() > Constants.EC_MAX_DGS_LISTING_NAME_LENGTH
                    || attachment.getDescription().length() > Constants.EC_MAX_DGS_LISTING_DESCRIPTION_LENGTH
                    || attachment.getTags().length() > Constants.EC_MAX_DGS_LISTING_TAGS_LENGTH
                    || attachment.getQuantity() < 0 || attachment.getQuantity() > Constants.EC_MAX_DGS_LISTING_QUANTITY
                    || attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.EC_MAX_BALANCE_NQT) {
                throw new EcNotValidExceptionEc("Invalid digital goods listing: " + attachment.getJSONObject());
            }
            PrunablePlainMessage prunablePlainMessage = transaction.getPrunablePlainMessage();
            if (prunablePlainMessage != null) {
                byte[] image = prunablePlainMessage.getMessage();
                if (image != null) {
                    Tika tika = new Tika();
                    MediaType mediaType = null;
                    try {
                        String mediaTypeName = tika.detect(image);
                        mediaType = MediaType.parse(mediaTypeName);
                    } catch (NoClassDefFoundError e) {
                        LoggerUtil.logError("Error running Tika parsers", e);
                    }
                    if (mediaType == null || !"image".equals(mediaType.getType())) {
                        throw new EcNotValidExceptionEc("Only image attachments allowed for DGS listing, media type is " + mediaType);
                    }
                }
            }
        }

        @Override
        boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK
                    && isDuplicate(com.inesv.ecchain.kernel.core.DigitalGoods.LISTING, getName(), duplicates, true);
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
    public static final TransactionType DELISTING = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_DELISTING;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_DELISTING;
        }

        @Override
        public String getName() {
            return "DigitalGoodsDelisting";
        }

        @Override
        Mortgaged.DigitalGoodsDelisting parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsDelisting(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsDelisting parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsDelisting(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsDelisting attachment = (Mortgaged.DigitalGoodsDelisting) transaction.getAttachment();
            ElectronicProductStore.delistGoods(attachment.getGoodsId());
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsDelisting attachment = (Mortgaged.DigitalGoodsDelisting) transaction.getAttachment();
            ElectronicProductStore.Goods goods = ElectronicProductStore.Goods.getGoods(attachment.getGoodsId());
            if (goods != null && transaction.getSenderId() != goods.getSellerId()) {
                throw new EcNotValidExceptionEc("Invalid digital goods delisting - seller is different: " + attachment.getJSONObject());
            }
            if (goods == null || goods.isDelisted()) {
                throw new EcNotCurrentlyValidExceptionEc("Goods " + Long.toUnsignedString(attachment.getGoodsId()) +
                        "not yet listed or already delisted");
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.DigitalGoodsDelisting attachment = (Mortgaged.DigitalGoodsDelisting) transaction.getAttachment();
            return isDuplicate(com.inesv.ecchain.kernel.core.DigitalGoods.DELISTING, Long.toUnsignedString(attachment.getGoodsId()), duplicates, true);
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
    public static final TransactionType PRICE_CHANGE = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_PRICE_CHANGE;
        }

        @Override
        public String getName() {
            return "DigitalGoodsPriceChange";
        }

        @Override
        Mortgaged.DigitalGoodsPriceChange parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsPriceChange(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsPriceChange parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsPriceChange(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsPriceChange attachment = (Mortgaged.DigitalGoodsPriceChange) transaction.getAttachment();
            ElectronicProductStore.changePrice(attachment.getGoodsId(), attachment.getPriceNQT());
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsPriceChange attachment = (Mortgaged.DigitalGoodsPriceChange) transaction.getAttachment();
            ElectronicProductStore.Goods goods = ElectronicProductStore.Goods.getGoods(attachment.getGoodsId());
            if (attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.EC_MAX_BALANCE_NQT
                    || (goods != null && transaction.getSenderId() != goods.getSellerId())) {
                throw new EcNotValidExceptionEc("Invalid digital goods price change: " + attachment.getJSONObject());
            }
            if (goods == null || goods.isDelisted()) {
                throw new EcNotCurrentlyValidExceptionEc("Goods " + Long.toUnsignedString(attachment.getGoodsId()) +
                        "not yet listed or already delisted");
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.DigitalGoodsPriceChange attachment = (Mortgaged.DigitalGoodsPriceChange) transaction.getAttachment();
            // not a bug, uniqueness is based on DigitalGoods.DELISTING
            return isDuplicate(com.inesv.ecchain.kernel.core.DigitalGoods.DELISTING, Long.toUnsignedString(attachment.getGoodsId()), duplicates, true);
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
    public static final TransactionType QUANTITY_CHANGE = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_QUANTITY_CHANGE;
        }

        @Override
        public String getName() {
            return "DigitalGoodsQuantityChange";
        }

        @Override
        Mortgaged.DigitalGoodsQuantityChange parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsQuantityChange(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsQuantityChange parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsQuantityChange(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsQuantityChange attachment = (Mortgaged.DigitalGoodsQuantityChange) transaction.getAttachment();
            ElectronicProductStore.changeQuantity(attachment.getGoodsId(), attachment.getDeltaQuantity());
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsQuantityChange attachment = (Mortgaged.DigitalGoodsQuantityChange) transaction.getAttachment();
            ElectronicProductStore.Goods goods = ElectronicProductStore.Goods.getGoods(attachment.getGoodsId());
            if (attachment.getDeltaQuantity() < -Constants.EC_MAX_DGS_LISTING_QUANTITY
                    || attachment.getDeltaQuantity() > Constants.EC_MAX_DGS_LISTING_QUANTITY
                    || (goods != null && transaction.getSenderId() != goods.getSellerId())) {
                throw new EcNotValidExceptionEc("Invalid digital goods quantity change: " + attachment.getJSONObject());
            }
            if (goods == null || goods.isDelisted()) {
                throw new EcNotCurrentlyValidExceptionEc("Goods " + Long.toUnsignedString(attachment.getGoodsId()) +
                        "not yet listed or already delisted");
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.DigitalGoodsQuantityChange attachment = (Mortgaged.DigitalGoodsQuantityChange) transaction.getAttachment();
            // not a bug, uniqueness is based on DigitalGoods.DELISTING
            return isDuplicate(com.inesv.ecchain.kernel.core.DigitalGoods.DELISTING, Long.toUnsignedString(attachment.getGoodsId()), duplicates, true);
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
    public static final TransactionType PURCHASE = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_PURCHASE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_PURCHASE;
        }

        @Override
        public String getName() {
            return "DigitalGoodsPurchase";
        }

        @Override
        Mortgaged.DigitalGoodsPurchase parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsPurchase(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsPurchase parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsPurchase(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.DigitalGoodsPurchase attachment = (Mortgaged.DigitalGoodsPurchase) transaction.getAttachment();
            if (senderAccount.getUnconfirmedBalanceNQT() >= Math.multiplyExact((long) attachment.getQuantity(), attachment.getPriceNQT())) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                        -Math.multiplyExact((long) attachment.getQuantity(), attachment.getPriceNQT()));
                return true;
            }
            return false;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.DigitalGoodsPurchase attachment = (Mortgaged.DigitalGoodsPurchase) transaction.getAttachment();
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                    Math.multiplyExact((long) attachment.getQuantity(), attachment.getPriceNQT()));
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsPurchase attachment = (Mortgaged.DigitalGoodsPurchase) transaction.getAttachment();
            ElectronicProductStore.purchase(transaction, attachment);
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsPurchase attachment = (Mortgaged.DigitalGoodsPurchase) transaction.getAttachment();
            ElectronicProductStore.Goods goods = ElectronicProductStore.Goods.getGoods(attachment.getGoodsId());
            if (attachment.getQuantity() <= 0 || attachment.getQuantity() > Constants.EC_MAX_DGS_LISTING_QUANTITY
                    || attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.EC_MAX_BALANCE_NQT
                    || (goods != null && goods.getSellerId() != transaction.getRecipientId())) {
                throw new EcNotValidExceptionEc("Invalid digital goods purchase: " + attachment.getJSONObject());
            }
            if (transaction.getEncryptedMessage() != null && !transaction.getEncryptedMessage().isText()) {
                throw new EcNotValidExceptionEc("Only text encrypted messages allowed");
            }
            if (goods == null || goods.isDelisted()) {
                throw new EcNotCurrentlyValidExceptionEc("Goods " + Long.toUnsignedString(attachment.getGoodsId()) +
                        "not yet listed or already delisted");
            }
            if (attachment.getQuantity() > goods.getQuantity() || attachment.getPriceNQT() != goods.getPriceNQT()) {
                throw new EcNotCurrentlyValidExceptionEc("Goods price or quantity changed: " + attachment.getJSONObject());
            }
            if (attachment.getDeliveryDeadlineTimestamp() <= EcBlockchainImpl.getInstance().getLastBlockTimestamp()) {
                throw new EcNotCurrentlyValidExceptionEc("Delivery deadline has already expired: " + attachment.getDeliveryDeadlineTimestamp());
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            if (EcBlockchainImpl.getInstance().getHeight() < Constants.EC_MONETARY_SYSTEM_BLOCK) {
                return false;
            }
            Mortgaged.DigitalGoodsPurchase attachment = (Mortgaged.DigitalGoodsPurchase) transaction.getAttachment();
            // not a bug, uniqueness is based on DigitalGoods.DELISTING
            return isDuplicate(com.inesv.ecchain.kernel.core.DigitalGoods.DELISTING, Long.toUnsignedString(attachment.getGoodsId()), duplicates, false);
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
    public static final TransactionType DELIVERY = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        private final Fee DGS_DELIVERY_FEE = new Fee.SizeBasedFee(Constants.ONE_EC, 2 * Constants.ONE_EC, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                Mortgaged.DigitalGoodsDelivery attachment = (Mortgaged.DigitalGoodsDelivery) transaction.getAttachment();
                return attachment.getGoodsDataLength() - 16;
            }
        };

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_DELIVERY;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_DELIVERY;
        }

        @Override
        public String getName() {
            return "DigitalGoodsDelivery";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            return DGS_DELIVERY_FEE;
        }

        @Override
        Mortgaged.DigitalGoodsDelivery parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsDelivery(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsDelivery parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            if (attachmentData.get("goodsData") == null) {
                return new Mortgaged.UnencryptedDigitalGoodsDelivery(attachmentData);
            }
            return new Mortgaged.DigitalGoodsDelivery(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsDelivery attachment = (Mortgaged.DigitalGoodsDelivery) transaction.getAttachment();
            ElectronicProductStore.deliver(transaction, attachment);
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsDelivery attachment = (Mortgaged.DigitalGoodsDelivery) transaction.getAttachment();
            ElectronicProductStore.Purchase purchase = ElectronicProductStore.Purchase.getPendingPurchase(attachment.getPurchaseId());
            if (attachment.getGoodsDataLength() > Constants.EC_MAX_DGS_GOODS_LENGTH) {
                throw new EcNotValidExceptionEc("Invalid digital goods delivery data length: " + attachment.getGoodsDataLength());
            }
            if (attachment.getGoods() != null) {
                if (attachment.getGoods().getData().length == 0 || attachment.getGoods().getNonce().length != 32) {
                    throw new EcNotValidExceptionEc("Invalid digital goods delivery: " + attachment.getJSONObject());
                }
            }
            if (attachment.getDiscountNQT() < 0 || attachment.getDiscountNQT() > Constants.EC_MAX_BALANCE_NQT
                    || (purchase != null &&
                    (purchase.getBuyerId() != transaction.getRecipientId()
                            || transaction.getSenderId() != purchase.getSellerId()
                            || attachment.getDiscountNQT() > Math.multiplyExact(purchase.getPriceNQT(), (long) purchase.getQuantity())))) {
                throw new EcNotValidExceptionEc("Invalid digital goods delivery: " + attachment.getJSONObject());
            }
            if (purchase == null || purchase.getEncryptedGoods() != null) {
                throw new EcNotCurrentlyValidExceptionEc("Purchase does not exist yet, or already delivered: "
                        + attachment.getJSONObject());
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.DigitalGoodsDelivery attachment = (Mortgaged.DigitalGoodsDelivery) transaction.getAttachment();
            return isDuplicate(com.inesv.ecchain.kernel.core.DigitalGoods.DELIVERY, Long.toUnsignedString(attachment.getPurchaseId()), duplicates, true);
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
    public static final TransactionType FEEDBACK = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_FEEDBACK;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_FEEDBACK;
        }

        @Override
        public String getName() {
            return "DigitalGoodsFeedback";
        }

        @Override
        Mortgaged.DigitalGoodsFeedback parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsFeedback(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsFeedback parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsFeedback(attachmentData);
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsFeedback attachment = (Mortgaged.DigitalGoodsFeedback) transaction.getAttachment();
            ElectronicProductStore.feedback(attachment.getPurchaseId(), transaction.getEncryptedMessage(), transaction.getMessage());
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsFeedback attachment = (Mortgaged.DigitalGoodsFeedback) transaction.getAttachment();
            ElectronicProductStore.Purchase purchase = ElectronicProductStore.Purchase.getPurchase(attachment.getPurchaseId());
            if (purchase != null &&
                    (purchase.getSellerId() != transaction.getRecipientId()
                            || transaction.getSenderId() != purchase.getBuyerId())) {
                throw new EcNotValidExceptionEc("Invalid digital goods feedback: " + attachment.getJSONObject());
            }
            if (transaction.getEncryptedMessage() == null && transaction.getMessage() == null) {
                throw new EcNotValidExceptionEc("Missing feedback message");
            }
            if (transaction.getEncryptedMessage() != null && !transaction.getEncryptedMessage().isText()) {
                throw new EcNotValidExceptionEc("Only text encrypted messages allowed");
            }
            if (transaction.getMessage() != null && !transaction.getMessage().isText()) {
                throw new EcNotValidExceptionEc("Only text public messages allowed");
            }
            if (purchase == null || purchase.getEncryptedGoods() == null) {
                throw new EcNotCurrentlyValidExceptionEc("Purchase does not exist yet or not yet delivered");
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
    public static final TransactionType REFUND = new com.inesv.ecchain.kernel.core.DigitalGoods() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_DIGITAL_GOODS_REFUND;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.DIGITAL_GOODS_REFUND;
        }

        @Override
        public String getName() {
            return "DigitalGoodsRefund";
        }

        @Override
        Mortgaged.DigitalGoodsRefund parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsRefund(buffer, transactionVersion);
        }

        @Override
        Mortgaged.DigitalGoodsRefund parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.DigitalGoodsRefund(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.DigitalGoodsRefund attachment = (Mortgaged.DigitalGoodsRefund) transaction.getAttachment();
            if (senderAccount.getUnconfirmedBalanceNQT() >= attachment.getRefundNQT()) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), -attachment.getRefundNQT());
                return true;
            }
            return false;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.DigitalGoodsRefund attachment = (Mortgaged.DigitalGoodsRefund) transaction.getAttachment();
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(), attachment.getRefundNQT());
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.DigitalGoodsRefund attachment = (Mortgaged.DigitalGoodsRefund) transaction.getAttachment();
            ElectronicProductStore.refund(getLedgerEvent(), transaction.getTransactionId(), transaction.getSenderId(),
                    attachment.getPurchaseId(), attachment.getRefundNQT(), transaction.getEncryptedMessage());
        }

        @Override
        void doValidateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.DigitalGoodsRefund attachment = (Mortgaged.DigitalGoodsRefund) transaction.getAttachment();
            ElectronicProductStore.Purchase purchase = ElectronicProductStore.Purchase.getPurchase(attachment.getPurchaseId());
            if (attachment.getRefundNQT() < 0 || attachment.getRefundNQT() > Constants.EC_MAX_BALANCE_NQT
                    || (purchase != null &&
                    (purchase.getBuyerId() != transaction.getRecipientId()
                            || transaction.getSenderId() != purchase.getSellerId()))) {
                throw new EcNotValidExceptionEc("Invalid digital goods refund: " + attachment.getJSONObject());
            }
            if (transaction.getEncryptedMessage() != null && !transaction.getEncryptedMessage().isText()) {
                throw new EcNotValidExceptionEc("Only text encrypted messages allowed");
            }
            if (purchase == null || purchase.getEncryptedGoods() == null || purchase.getRefundNQT() != 0) {
                throw new EcNotCurrentlyValidExceptionEc("Purchase does not exist or is not delivered or is already refunded");
            }
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.DigitalGoodsRefund attachment = (Mortgaged.DigitalGoodsRefund) transaction.getAttachment();
            return isDuplicate(com.inesv.ecchain.kernel.core.DigitalGoods.REFUND, Long.toUnsignedString(attachment.getPurchaseId()), duplicates, true);
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

    private DigitalGoods() {
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_DIGITAL_GOODS;
    }

    @Override
    boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
    }

    @Override
    void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    @Override
    final void validateAttachment(Transaction transaction) throws EcValidationException {
        if (transaction.getAmountNQT() != 0) {
            throw new EcNotValidExceptionEc("Invalid digital goods transaction");
        }
        doValidateAttachment(transaction);
    }

    abstract void doValidateAttachment(Transaction transaction) throws EcValidationException;

}
