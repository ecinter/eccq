package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.crypto.HashFunction;

import java.util.EnumSet;
import java.util.Set;


public enum CoinType {


    EXCHANGEABLE(0x01) {
        @Override
        void validate(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcNotValidExceptionEc {
        }

        @Override
        void validateMissing(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcNotValidExceptionEc {
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_ISSUANCE) {
                if (!validators.contains(CLAIMABLE)) {
                    throw new EcNotValidExceptionEc("Coin is not exchangeable and not claimable");
                }
            }
            if (transaction.getTransactionType() instanceof CoinageExchange || transaction.getTransactionType() == Coinage.EC_PUBLISH_EXCHANGE_OFFER) {
                throw new EcNotValidExceptionEc("Coin is not exchangeable");
            }
        }
    },

    CONTROLLABLE(0x02) {
        @Override
        void validate(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcNotValidExceptionEc {
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_TRANSFER) {
                if (coin == null || (coin.getAccountId() != transaction.getSenderId() && coin.getAccountId() != transaction.getRecipientId())) {
                    throw new EcNotValidExceptionEc("Controllable coin can only be transferred to/from issuer account");
                }
            }
            if (transaction.getTransactionType() == Coinage.EC_PUBLISH_EXCHANGE_OFFER) {
                if (coin == null || coin.getAccountId() != transaction.getSenderId()) {
                    throw new EcNotValidExceptionEc("Only coin issuer can publish an exchange offer for controllable coin");
                }
            }
        }

        @Override
        void validateMissing(Coin coin, Transaction transaction, Set<CoinType> validators) {
        }

    },

    RESERVABLE(0x04) {
        @Override
        void validate(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcValidationException {
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_ISSUANCE) {
                Mortgaged.MonetarySystemCurrencyIssuance attachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
                int issuanceHeight = attachment.getIssuanceHeight();
                int finishHeight = attachment.getFinishValidationHeight(transaction);
                if (issuanceHeight <= finishHeight) {
                    throw new EcNotCurrentlyValidExceptionEc(
                            String.format("Reservable coin activation height %d not higher than transaction apply height %d",
                                    issuanceHeight, finishHeight));
                }
                if (attachment.getMinReservePerUnitNQT() <= 0) {
                    throw new EcNotValidExceptionEc("Minimum reserve per unit must be > 0");
                }
                if (Math.multiplyExact(attachment.getMinReservePerUnitNQT(), attachment.getReserveSupply()) > Constants.EC_MAX_BALANCE_NQT) {
                    throw new EcNotValidExceptionEc("Minimum reserve per unit is too large");
                }
                if (attachment.getReserveSupply() <= attachment.getInitialSupply()) {
                    throw new EcNotValidExceptionEc("Reserve supply must exceed initial supply");
                }
                if (!validators.contains(MINTABLE) && attachment.getReserveSupply() < attachment.getMaxSupply()) {
                    throw new EcNotValidExceptionEc("Max supply must not exceed reserve supply for reservable and non-mintable coin");
                }
            }
            if (transaction.getTransactionType() == Coinage.EC_RESERVE_INCREASE) {
                Mortgaged.MonetarySystemReserveIncrease attachment = (Mortgaged.MonetarySystemReserveIncrease) transaction.getAttachment();
                if (coin != null && coin.getIssuanceHeight() <= attachment.getFinishValidationHeight(transaction)) {
                    throw new EcNotCurrentlyValidExceptionEc("Cannot increase reserve for active coin");
                }
            }
        }

        @Override
        void validateMissing(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcNotValidExceptionEc {
            if (transaction.getTransactionType() == Coinage.EC_RESERVE_INCREASE) {
                throw new EcNotValidExceptionEc("Cannot increase reserve since coin is not reservable");
            }
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_ISSUANCE) {
                Mortgaged.MonetarySystemCurrencyIssuance attachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
                if (attachment.getIssuanceHeight() != 0) {
                    throw new EcNotValidExceptionEc("Issuance height for non-reservable coin must be 0");
                }
                if (attachment.getMinReservePerUnitNQT() > 0) {
                    throw new EcNotValidExceptionEc("Minimum reserve per unit for non-reservable coin must be 0 ");
                }
                if (attachment.getReserveSupply() > 0) {
                    throw new EcNotValidExceptionEc("Reserve supply for non-reservable coin must be 0");
                }
                if (!validators.contains(MINTABLE) && attachment.getInitialSupply() < attachment.getMaxSupply()) {
                    throw new EcNotValidExceptionEc("Initial supply for non-reservable and non-mintable coin must be equal to max supply");
                }
            }
        }
    },

    CLAIMABLE(0x08) {
        @Override
        void validate(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcValidationException {
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_ISSUANCE) {
                Mortgaged.MonetarySystemCurrencyIssuance attachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
                if (!validators.contains(RESERVABLE)) {
                    throw new EcNotValidExceptionEc("Claimable coin must be reservable");
                }
                if (validators.contains(MINTABLE)) {
                    throw new EcNotValidExceptionEc("Claimable coin cannot be mintable");
                }
                if (attachment.getInitialSupply() > 0) {
                    throw new EcNotValidExceptionEc("Claimable coin must have initial supply 0");
                }
            }
            if (transaction.getTransactionType() == Coinage.EC_RESERVE_CLAIM) {
                if (coin == null || !coin.isActive()) {
                    throw new EcNotCurrentlyValidExceptionEc("Cannot claim reserve since coin is not yet active");
                }
            }
        }

        @Override
        void validateMissing(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcNotValidExceptionEc {
            if (transaction.getTransactionType() == Coinage.EC_RESERVE_CLAIM) {
                throw new EcNotValidExceptionEc("Cannot claim reserve since coin is not claimable");
            }
        }
    },

    MINTABLE(0x10) {
        @Override
        void validate(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcNotValidExceptionEc {
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_ISSUANCE) {
                Mortgaged.MonetarySystemCurrencyIssuance issuanceAttachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
                try {
                    HashFunction hashFunction = HashFunction.getHashFunction(issuanceAttachment.getAlgorithm());
                    if (!CoinMinting.ACCEPTED_HASH_FUNCTIONS.contains(hashFunction)) {
                        throw new EcNotValidExceptionEc("Invalid minting algorithm " + hashFunction);
                    }
                } catch (IllegalArgumentException e) {
                    throw new EcNotValidExceptionEc("Illegal algorithm code specified", e);
                }
                if (issuanceAttachment.getMinDifficulty() < 1 || issuanceAttachment.getMaxDifficulty() > 255 ||
                        issuanceAttachment.getMaxDifficulty() < issuanceAttachment.getMinDifficulty()) {
                    throw new EcNotValidExceptionEc(
                            String.format("Invalid minting difficulties min %d max %d, difficulty must be between 1 and 255, max larger than min",
                                    issuanceAttachment.getMinDifficulty(), issuanceAttachment.getMaxDifficulty()));
                }
                if (issuanceAttachment.getMaxSupply() <= issuanceAttachment.getReserveSupply()) {
                    throw new EcNotValidExceptionEc("Max supply for mintable coin must exceed reserve supply");
                }
            }
        }

        @Override
        void validateMissing(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcNotValidExceptionEc {
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_ISSUANCE) {
                Mortgaged.MonetarySystemCurrencyIssuance issuanceAttachment = (Mortgaged.MonetarySystemCurrencyIssuance) transaction.getAttachment();
                if (issuanceAttachment.getMinDifficulty() != 0 ||
                        issuanceAttachment.getMaxDifficulty() != 0 ||
                        issuanceAttachment.getAlgorithm() != 0) {
                    throw new EcNotValidExceptionEc("Non mintable coin should not specify algorithm or difficulty");
                }
            }
            if (transaction.getTransactionType() == Coinage.EC_CURRENCY_MINTING) {
                throw new EcNotValidExceptionEc("Coin is not mintable");
            }
        }

    },

    NON_SHUFFLEABLE(0x20) {
        @Override
        void validate(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcValidationException {
            if (transaction.getTransactionType() == ShufflingTransaction.SHUFFLING_CREATION) {
                throw new EcNotValidExceptionEc("Shuffling is not allowed for this coin");
            }
        }

        @Override
        void validateMissing(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcValidationException {
        }
    };

    private final int code;

    CoinType(int code) {
        this.code = code;
    }

    public static CoinType get(int code) {
        for (CoinType coinType : values()) {
            if (coinType.getCode() == code) {
                return coinType;
            }
        }
        return null;
    }

    static void validate(Coin coin, Transaction transaction) throws EcValidationException {
        if (coin == null) {
            throw new EcNotCurrentlyValidExceptionEc("Unknown coin: " + transaction.getAttachment().getJSONObject());
        }
        validate(coin, coin.getCoinType(), transaction);
    }

    static void validate(int type, Transaction transaction) throws EcValidationException {
        validate(null, type, transaction);
    }

    private static void validate(Coin coin, int type, Transaction transaction) throws EcValidationException {
        if (transaction.getAmountNQT() != 0) {
            throw new EcNotValidExceptionEc("Coin transaction EC amount must be 0");
        }

        final EnumSet<CoinType> validators = EnumSet.noneOf(CoinType.class);
        for (CoinType validator : CoinType.values()) {
            if ((validator.getCode() & type) != 0) {
                validators.add(validator);
            }
        }
        if (validators.isEmpty()) {
            throw new EcNotValidExceptionEc("Coin type not specified");
        }
        for (CoinType validator : CoinType.values()) {
            if ((validator.getCode() & type) != 0) {
                validator.validate(coin, transaction, validators);
            } else {
                validator.validateMissing(coin, transaction, validators);
            }
        }
    }

    static void validateCurrencyNaming(long issuerAccountId, Mortgaged.MonetarySystemCurrencyIssuance attachment) throws EcValidationException {
        String name = attachment.getName();
        String code = attachment.getCode();
        String description = attachment.getDescription();
        if (name.length() < Constants.EC_MIN_CURRENCY_NAME_LENGTH || name.length() > Constants.EC_MAX_CURRENCY_NAME_LENGTH
                || name.length() < code.length()
                || code.length() < Constants.EC_MIN_CURRENCY_CODE_LENGTH || code.length() > Constants.EC_MAX_CURRENCY_CODE_LENGTH
                || description.length() > Constants.EC_MAX_CURRENCY_DESCRIPTION_LENGTH) {
            throw new EcNotValidExceptionEc(String.format("Invalid coin name %s code %s or description %s", name, code, description));
        }
        String normalizedName = name.toLowerCase();
        for (int i = 0; i < normalizedName.length(); i++) {
            if (Constants.EC_ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
                throw new EcNotValidExceptionEc("Invalid coin name: " + normalizedName);
            }
        }
        for (int i = 0; i < code.length(); i++) {
            if (Constants.ALLOWED_CURRENCY_CODE_LETTERS.indexOf(code.charAt(i)) < 0) {
                throw new EcNotValidExceptionEc("Invalid coin code: " + code + " code must be all upper case");
            }
        }
        if (code.contains("EC") || code.contains("NEXT") || "ec".equals(normalizedName) || "next".equals(normalizedName)) {
            throw new EcNotValidExceptionEc("Coin name already used");
        }
        Coin coin;
        if ((coin = Coin.getCoinByName(normalizedName)) != null && !coin.canBeDeletedBy(issuerAccountId)) {
            throw new EcNotCurrentlyValidExceptionEc("Coin name already used: " + normalizedName);
        }
        if ((coin = Coin.getCoinByCode(name)) != null && !coin.canBeDeletedBy(issuerAccountId)) {
            throw new EcNotCurrentlyValidExceptionEc("Coin name already used as code: " + normalizedName);
        }
        if ((coin = Coin.getCoinByCode(code)) != null && !coin.canBeDeletedBy(issuerAccountId)) {
            throw new EcNotCurrentlyValidExceptionEc("Coin code already used: " + code);
        }
        if ((coin = Coin.getCoinByName(code)) != null && !coin.canBeDeletedBy(issuerAccountId)) {
            throw new EcNotCurrentlyValidExceptionEc("Coin code already used as name: " + code);
        }
    }

    public int getCode() {
        return code;
    }

    abstract void validate(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcValidationException;

    abstract void validateMissing(Coin coin, Transaction transaction, Set<CoinType> validators) throws EcValidationException;

}
