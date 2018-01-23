package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;

public abstract class Coinage extends TransactionType {

    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_ISSUANCE = 0;
    public static final TransactionType EC_CURRENCY_ISSUANCE = new Coinage() {

        private final Fee FIVE_LETTER_CURRENCY_ISSUANCE_FEE = new Fee.ConstantFee(40 * Constants.ONE_EC);
        private final Fee FOUR_LETTER_CURRENCY_ISSUANCE_FEE = new Fee.ConstantFee(1000 * Constants.ONE_EC);
        private final Fee THREE_LETTER_CURRENCY_ISSUANCE_FEE = new Fee.ConstantFee(25000 * Constants.ONE_EC);

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_ISSUANCE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_ISSUANCE;
        }

        @Override
        public String getName() {
            return "CurrencyIssuance";
        }

        @Override
        Fee getBaselineFee(Transaction transaction) {
            Mortgaged.MonetarySystemCurrencyIssuance attachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
            if (Coin.getCoinByCode(attachment.getCode()) != null || Coin.getCoinByCode(attachment.getName()) != null
                    || Coin.getCoinByName(attachment.getName()) != null || Coin.getCoinByName(attachment.getCode()) != null) {
                return FIVE_LETTER_CURRENCY_ISSUANCE_FEE;
            }
            switch (Math.min(attachment.getCode().length(), attachment.getName().length())) {
                case 3:
                    return THREE_LETTER_CURRENCY_ISSUANCE_FEE;
                case 4:
                    return FOUR_LETTER_CURRENCY_ISSUANCE_FEE;
                case 5:
                    return FIVE_LETTER_CURRENCY_ISSUANCE_FEE;
                default:
                    // never, invalid code length will be checked and caught later
                    return THREE_LETTER_CURRENCY_ISSUANCE_FEE;
            }
        }

        @Override
        long[] getBackFees(Transaction transaction) {
            long feeNQT = transaction.getFeeNQT();
            return new long[]{feeNQT * 3 / 10, feeNQT * 2 / 10, feeNQT / 10};
        }

        @Override
        Mortgaged.MonetarySystemCurrencyIssuance parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyIssuance(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemCurrencyIssuance parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyIssuance(attachmentData);
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MonetarySystemCurrencyIssuance attachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
            String nameLower = attachment.getName().toLowerCase();
            String codeLower = attachment.getCode().toLowerCase();
            boolean isDuplicate = TransactionType.isDuplicate(EC_CURRENCY_ISSUANCE, nameLower, duplicates, true);
            if (!nameLower.equals(codeLower)) {
                isDuplicate = isDuplicate || TransactionType.isDuplicate(EC_CURRENCY_ISSUANCE, codeLower, duplicates, true);
            }
            return isDuplicate;
        }

        @Override
        boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            return EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK
                    && isDuplicate(EC_CURRENCY_ISSUANCE, getName(), duplicates, true);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MonetarySystemCurrencyIssuance attachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
            if (attachment.getMaxSupply() > Constants.EC_MAX_CURRENCY_TOTAL_SUPPLY
                    || attachment.getMaxSupply() <= 0
                    || attachment.getInitialSupply() < 0
                    || attachment.getInitialSupply() > attachment.getMaxSupply()
                    || attachment.getReserveSupply() < 0
                    || attachment.getReserveSupply() > attachment.getMaxSupply()
                    || attachment.getIssuanceHeight() < 0
                    || attachment.getMinReservePerUnitNQT() < 0
                    || attachment.getDecimals() < 0 || attachment.getDecimals() > 8
                    || attachment.getRuleset() != 0) {
                throw new EcNotValidExceptionEc("Invalid currency issuance: " + attachment.getJSONObject());
            }
            int t = 1;
            for (int i = 0; i < 32; i++) {
                if ((t & attachment.getType()) != 0 && CoinType.get(t) == null) {
                    throw new EcNotValidExceptionEc("Invalid currency type: " + attachment.getType());
                }
                t <<= 1;
            }
            CoinType.validate(attachment.getType(), transaction);
            CoinType.validateCurrencyNaming(transaction.getSenderId(), attachment);
        }


        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemCurrencyIssuance attachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
            long transactionId = transaction.getTransactionId();
            Coin.addCoin(getLedgerEvent(), transactionId, transaction, senderAccount, attachment);
            senderAccount.addToCurrencyAndUnconfirmedCurrencyUnits(getLedgerEvent(), transactionId,
                    transactionId, attachment.getInitialSupply());
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_RESERVE_INCREASE = 1;
    public static final TransactionType EC_RESERVE_INCREASE = new Coinage() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_RESERVE_INCREASE;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_RESERVE_INCREASE;
        }

        @Override
        public String getName() {
            return "ReserveIncrease";
        }

        @Override
        Mortgaged.MonetarySystemReserveIncrease parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemReserveIncrease(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemReserveIncrease parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemReserveIncrease(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MonetarySystemReserveIncrease attachment = (Mortgaged.MonetarySystemReserveIncrease) transaction.getAttachment();
            if (attachment.getAmountPerUnitNQT() <= 0) {
                throw new EcNotValidExceptionEc("Reserve increase EC amount must be positive: " + attachment.getAmountPerUnitNQT());
            }
            CoinType.validate(Coin.getCoin(attachment.getCurrencyId()), transaction);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemReserveIncrease attachment = (Mortgaged.MonetarySystemReserveIncrease) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            if (senderAccount.getUnconfirmedBalanceNQT() >= Math.multiplyExact(coin.getReserveSupply(), attachment.getAmountPerUnitNQT())) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                        -Math.multiplyExact(coin.getReserveSupply(), attachment.getAmountPerUnitNQT()));
                return true;
            }
            return false;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemReserveIncrease attachment = (Mortgaged.MonetarySystemReserveIncrease) transaction.getAttachment();
            long reserveSupply;
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            if (coin != null) {
                reserveSupply = coin.getReserveSupply();
            } else { // coin must have been deleted, get reserve supply from the original issuance transaction
                Transaction currencyIssuance = EcBlockchainImpl.getInstance().getTransaction(attachment.getCurrencyId());
                Mortgaged.MonetarySystemCurrencyIssuance currencyIssuanceAttachment = (Mortgaged.MonetarySystemCurrencyIssuance) currencyIssuance.getAttachment();
                reserveSupply = currencyIssuanceAttachment.getReserveSupply();
            }
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                    Math.multiplyExact(reserveSupply, attachment.getAmountPerUnitNQT()));
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemReserveIncrease attachment = (Mortgaged.MonetarySystemReserveIncrease) transaction.getAttachment();
            Coin.increaseReserve(getLedgerEvent(), transaction.getTransactionId(), senderAccount, attachment.getCurrencyId(),
                    attachment.getAmountPerUnitNQT());
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_RESERVE_CLAIM = 2;
    public static final TransactionType EC_RESERVE_CLAIM = new Coinage() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_RESERVE_CLAIM;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_RESERVE_CLAIM;
        }

        @Override
        public String getName() {
            return "ReserveClaim";
        }

        @Override
        Mortgaged.MonetarySystemReserveClaim parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemReserveClaim(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemReserveClaim parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemReserveClaim(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MonetarySystemReserveClaim attachment = (Mortgaged.MonetarySystemReserveClaim) transaction.getAttachment();
            if (attachment.getUnits() <= 0) {
                throw new EcNotValidExceptionEc("Reserve claim number of units must be positive: " + attachment.getUnits());
            }
            CoinType.validate(Coin.getCoin(attachment.getCurrencyId()), transaction);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemReserveClaim attachment = (Mortgaged.MonetarySystemReserveClaim) transaction.getAttachment();
            if (senderAccount.getUnconfirmedCoinUnits(attachment.getCurrencyId()) >= attachment.getUnits()) {
                senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getCurrencyId(), -attachment.getUnits());
                return true;
            }
            return false;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemReserveClaim attachment = (Mortgaged.MonetarySystemReserveClaim) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            if (coin != null) {
                senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(), attachment.getCurrencyId(),
                        attachment.getUnits());
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemReserveClaim attachment = (Mortgaged.MonetarySystemReserveClaim) transaction.getAttachment();
            Coin.claimReserve(getLedgerEvent(), transaction.getTransactionId(), senderAccount, attachment.getCurrencyId(),
                    attachment.getUnits());
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_TRANSFER = 3;
    public static final TransactionType EC_CURRENCY_TRANSFER = new Coinage() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_TRANSFER;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_TRANSFER;
        }

        @Override
        public String getName() {
            return "CoinTransfer";
        }

        @Override
        Mortgaged.MonetarySystemCurrencyTransfer parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyTransfer(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemCurrencyTransfer parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyTransfer(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MonetarySystemCurrencyTransfer attachment = (Mortgaged.MonetarySystemCurrencyTransfer) transaction.getAttachment();
            if (attachment.getUnits() <= 0) {
                throw new EcNotValidExceptionEc("Invalid coin transfer: " + attachment.getJSONObject());
            }
            if (transaction.getRecipientId() == Genesis.EC_CREATOR_ID) {
                throw new EcNotValidExceptionEc("Coin transfer to genesis account not allowed");
            }
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            CoinType.validate(coin, transaction);
            if (!coin.isActive()) {
                throw new EcNotCurrentlyValidExceptionEc("Coin not currently active: " + attachment.getJSONObject());
            }
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemCurrencyTransfer attachment = (Mortgaged.MonetarySystemCurrencyTransfer) transaction.getAttachment();
            if (attachment.getUnits() > senderAccount.getUnconfirmedCoinUnits(attachment.getCurrencyId())) {
                return false;
            }
            senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(),
                    attachment.getCurrencyId(), -attachment.getUnits());
            return true;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemCurrencyTransfer attachment = (Mortgaged.MonetarySystemCurrencyTransfer) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            if (coin != null) {
                senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getCurrencyId(), attachment.getUnits());
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemCurrencyTransfer attachment = (Mortgaged.MonetarySystemCurrencyTransfer) transaction.getAttachment();
            Coin.transferCoin(getLedgerEvent(), transaction.getTransactionId(), senderAccount, recipientAccount,
                    attachment.getCurrencyId(), attachment.getUnits());
            CoinTransfer.addTransfer(transaction, attachment);
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_PUBLISH_EXCHANGE_OFFER = 4;
    public static final TransactionType EC_PUBLISH_EXCHANGE_OFFER = new Coinage() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_PUBLISH_EXCHANGE_OFFER;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_PUBLISH_EXCHANGE_OFFER;
        }

        @Override
        public String getName() {
            return "PublishExchangeOffer";
        }

        @Override
        Mortgaged.MonetarySystemPublishExchangeOffer parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemPublishExchangeOffer(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemPublishExchangeOffer parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemPublishExchangeOffer(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
            if (attachment.getBuyRateNQT() <= 0
                    || attachment.getSellRateNQT() <= 0
                    || attachment.getBuyRateNQT() > attachment.getSellRateNQT()) {
                throw new EcNotValidExceptionEc(String.format("Invalid exchange offer, buy rate %d and sell rate %d has to be larger than 0, buy rate cannot be larger than sell rate",
                        attachment.getBuyRateNQT(), attachment.getSellRateNQT()));
            }
            if (attachment.getTotalBuyLimit() < 0
                    || attachment.getTotalSellLimit() < 0
                    || attachment.getInitialBuySupply() < 0
                    || attachment.getInitialSellSupply() < 0
                    || attachment.getExpirationHeight() < 0) {
                throw new EcNotValidExceptionEc("Invalid exchange offer, units and height cannot be negative: " + attachment.getJSONObject());
            }
            if (attachment.getTotalBuyLimit() < attachment.getInitialBuySupply()
                    || attachment.getTotalSellLimit() < attachment.getInitialSellSupply()) {
                throw new EcNotValidExceptionEc("Initial supplies must not exceed total limits");
            }
            if (EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK) {
                if (attachment.getTotalBuyLimit() == 0 && attachment.getTotalSellLimit() == 0) {
                    throw new EcNotValidExceptionEc("Total buy and sell limits cannot be both 0");
                }
                if (attachment.getInitialBuySupply() == 0 && attachment.getInitialSellSupply() == 0) {
                    throw new EcNotValidExceptionEc("Initial buy and sell supply cannot be both 0");
                }
            }
            if (attachment.getExpirationHeight() <= attachment.getFinishValidationHeight(transaction)) {
                throw new EcNotCurrentlyValidExceptionEc("Expiration height must be after transaction execution height");
            }
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            CoinType.validate(coin, transaction);
            if (!coin.isActive()) {
                throw new EcNotCurrentlyValidExceptionEc("Coin not currently active: " + attachment.getJSONObject());
            }
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
            if (senderAccount.getUnconfirmedBalanceNQT() >= Math.multiplyExact(attachment.getInitialBuySupply(), attachment.getBuyRateNQT())
                    && senderAccount.getUnconfirmedCoinUnits(attachment.getCurrencyId()) >= attachment.getInitialSellSupply()) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                        -Math.multiplyExact(attachment.getInitialBuySupply(), attachment.getBuyRateNQT()));
                senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getCurrencyId(), -attachment.getInitialSellSupply());
                return true;
            }
            return false;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                    Math.multiplyExact(attachment.getInitialBuySupply(), attachment.getBuyRateNQT()));
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            if (coin != null) {
                senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getCurrencyId(), attachment.getInitialSellSupply());
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
            CoinExchangeOffer.publishOffer(transaction, attachment);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_EXCHANGE_BUY = 5;
    public static final TransactionType EC_EXCHANGE_BUY = new CoinageExchange() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_EXCHANGE_BUY;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_EXCHANGE_BUY;
        }

        @Override
        public String getName() {
            return "ExchangeBuy";
        }

        @Override
        Mortgaged.MonetarySystemExchangeBuy parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemExchangeBuy(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemExchangeBuy parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemExchangeBuy(attachmentData);
        }


        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemExchangeBuy attachment = (Mortgaged.MonetarySystemExchangeBuy) transaction.getAttachment();
            if (senderAccount.getUnconfirmedBalanceNQT() >= Math.multiplyExact(attachment.getUnits(), attachment.getRateNQT())) {
                senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                        -Math.multiplyExact(attachment.getUnits(), attachment.getRateNQT()));
                return true;
            }
            return false;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemExchangeBuy attachment = (Mortgaged.MonetarySystemExchangeBuy) transaction.getAttachment();
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getTransactionId(),
                    Math.multiplyExact(attachment.getUnits(), attachment.getRateNQT()));
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemExchangeBuy attachment = (Mortgaged.MonetarySystemExchangeBuy) transaction.getAttachment();
            ConversionRequest.addExchangeRequest(transaction, attachment);
            CoinExchangeOffer.exchangeECForCurrency(transaction, senderAccount, attachment.getCurrencyId(), attachment.getRateNQT(), attachment.getUnits());
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_EXCHANGE_SELL = 6;
    public static final TransactionType EC_EXCHANGE_SELL = new CoinageExchange() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_EXCHANGE_SELL;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_EXCHANGE_SELL;
        }

        @Override
        public String getName() {
            return "ExchangeSell";
        }

        @Override
        Mortgaged.MonetarySystemExchangeSell parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemExchangeSell(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemExchangeSell parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemExchangeSell(attachmentData);
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemExchangeSell attachment = (Mortgaged.MonetarySystemExchangeSell) transaction.getAttachment();
            if (senderAccount.getUnconfirmedCoinUnits(attachment.getCurrencyId()) >= attachment.getUnits()) {
                senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getCurrencyId(), -attachment.getUnits());
                return true;
            }
            return false;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            Mortgaged.MonetarySystemExchangeSell attachment = (Mortgaged.MonetarySystemExchangeSell) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            if (coin != null) {
                senderAccount.addToUnconfirmedCurrencyUnits(getLedgerEvent(), transaction.getTransactionId(),
                        attachment.getCurrencyId(), attachment.getUnits());
            }
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemExchangeSell attachment = (Mortgaged.MonetarySystemExchangeSell) transaction.getAttachment();
            ConversionRequest.addExchangeRequest(transaction, attachment);
            CoinExchangeOffer.exchangeCurrencyForEC(transaction, senderAccount, attachment.getCurrencyId(), attachment.getRateNQT(), attachment.getUnits());
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_MINTING = 7;
    public static final TransactionType EC_CURRENCY_MINTING = new Coinage() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_MINTING;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_MINTING;
        }

        @Override
        public String getName() {
            return "CoinMinting";
        }

        @Override
        Mortgaged.MonetarySystemCurrencyMinting parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyMinting(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemCurrencyMinting parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyMinting(attachmentData);
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MonetarySystemCurrencyMinting attachment = (Mortgaged.MonetarySystemCurrencyMinting) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            CoinType.validate(coin, transaction);
            if (attachment.getUnits() <= 0) {
                throw new EcNotValidExceptionEc("Invalid number of units: " + attachment.getUnits());
            }
            if (attachment.getUnits() > (coin.getMaxSupply() - coin.getReserveSupply()) / Constants.EC_MAX_MINTING_RATIO) {
                throw new EcNotValidExceptionEc(String.format("Cannot mint more than 1/%d of the total units supply in a single request", Constants.EC_MAX_MINTING_RATIO));
            }
            if (!coin.isActive()) {
                throw new EcNotCurrentlyValidExceptionEc("Coin not currently active " + attachment.getJSONObject());
            }
            long counter = CoinMint.getCounter(attachment.getCurrencyId(), transaction.getSenderId());
            if (attachment.getCounter() <= counter) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Counter %d has to be bigger than %d", attachment.getCounter(), counter));
            }
            if (!CoinMinting.meetsTarget(transaction.getSenderId(), coin, attachment)) {
                throw new EcNotCurrentlyValidExceptionEc(String.format("Hash doesn't meet target %s", attachment.getJSONObject()));
            }
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemCurrencyMinting attachment = (Mortgaged.MonetarySystemCurrencyMinting) transaction.getAttachment();
            CoinMint.mintCurrency(getLedgerEvent(), transaction.getTransactionId(), senderAccount, attachment);
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MonetarySystemCurrencyMinting attachment = (Mortgaged.MonetarySystemCurrencyMinting) transaction.getAttachment();
            return TransactionType.isDuplicate(EC_CURRENCY_MINTING, attachment.getCurrencyId() + ":" + transaction.getSenderId(), duplicates, true)
                    || super.isDuplicate(transaction, duplicates);
        }

        @Override
        boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MonetarySystemCurrencyMinting attachment = (Mortgaged.MonetarySystemCurrencyMinting) transaction.getAttachment();
            return TransactionType.isDuplicate(EC_CURRENCY_MINTING, attachment.getCurrencyId() + ":" + transaction.getSenderId(), duplicates, true);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

    };
    private static final byte EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_DELETION = 8;
    public static final TransactionType EC_CURRENCY_DELETION = new Coinage() {

        @Override
        public byte getSubtype() {
            return EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_DELETION;
        }

        @Override
        public LedgerEvent getLedgerEvent() {
            return LedgerEvent.CURRENCY_DELETION;
        }

        @Override
        public String getName() {
            return "CurrencyDeletion";
        }

        @Override
        Mortgaged.MonetarySystemCurrencyDeletion parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyDeletion(buffer, transactionVersion);
        }

        @Override
        Mortgaged.MonetarySystemCurrencyDeletion parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return new Mortgaged.MonetarySystemCurrencyDeletion(attachmentData);
        }

        @Override
        boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
            Mortgaged.MonetarySystemCurrencyDeletion attachment = (Mortgaged.MonetarySystemCurrencyDeletion) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            String nameLower = coin.getName().toLowerCase();
            String codeLower = coin.getCoinCode().toLowerCase();
            boolean isDuplicate = TransactionType.isDuplicate(EC_CURRENCY_ISSUANCE, nameLower, duplicates, true);
            if (!nameLower.equals(codeLower)) {
                isDuplicate = isDuplicate || TransactionType.isDuplicate(EC_CURRENCY_ISSUANCE, codeLower, duplicates, true);
            }
            return isDuplicate;
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            Mortgaged.MonetarySystemCurrencyDeletion attachment = (Mortgaged.MonetarySystemCurrencyDeletion) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            CoinType.validate(coin, transaction);
            if (!coin.canBeDeletedBy(transaction.getSenderId())) {
                throw new EcNotCurrentlyValidExceptionEc("Coin " + Long.toUnsignedString(coin.getId()) + " cannot be deleted by account " +
                        Long.toUnsignedString(transaction.getSenderId()));
            }
        }

        @Override
        boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Mortgaged.MonetarySystemCurrencyDeletion attachment = (Mortgaged.MonetarySystemCurrencyDeletion) transaction.getAttachment();
            Coin coin = Coin.getCoin(attachment.getCurrencyId());
            coin.delete(getLedgerEvent(), transaction.getTransactionId(), senderAccount);
        }

        @Override
        public boolean canHaveRecipient() {
            return false;
        }

    };

    static TransactionType findTransactionType(byte subtype) {
        switch (subtype) {
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_ISSUANCE:
                return Coinage.EC_CURRENCY_ISSUANCE;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_RESERVE_INCREASE:
                return Coinage.EC_RESERVE_INCREASE;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_RESERVE_CLAIM:
                return Coinage.EC_RESERVE_CLAIM;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_TRANSFER:
                return Coinage.EC_CURRENCY_TRANSFER;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_PUBLISH_EXCHANGE_OFFER:
                return Coinage.EC_PUBLISH_EXCHANGE_OFFER;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_EXCHANGE_BUY:
                return Coinage.EC_EXCHANGE_BUY;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_EXCHANGE_SELL:
                return Coinage.EC_EXCHANGE_SELL;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_MINTING:
                return Coinage.EC_CURRENCY_MINTING;
            case Coinage.EC_SUBTYPE_MONETARY_SYSTEM_CURRENCY_DELETION:
                return Coinage.EC_CURRENCY_DELETION;
            default:
                return null;
        }
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_MONETARY_SYSTEM;
    }

    @Override
    boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        Mortgaged.MonetarySystemAttachment attachment = (Mortgaged.MonetarySystemAttachment) transaction.getAttachment();
        Coin coin = Coin.getCoin(attachment.getCurrencyId());
        String nameLower = coin.getName().toLowerCase();
        String codeLower = coin.getCoinCode().toLowerCase();
        boolean isDuplicate = TransactionType.isDuplicate(EC_CURRENCY_ISSUANCE, nameLower, duplicates, false);
        if (!nameLower.equals(codeLower)) {
            isDuplicate = isDuplicate || TransactionType.isDuplicate(EC_CURRENCY_ISSUANCE, codeLower, duplicates, false);
        }
        return isDuplicate;
    }

    @Override
    public final boolean isPhasingSafe() {
        return false;
    }

}
