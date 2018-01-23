package com.inesv.ecchain.kernel.core;

public enum HoldingType {

    EC((byte) 0) {

        @Override
        public long getUnconfirmedBalance(Account account, long holdingId) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            return account.getUnconfirmedBalanceNQT();
        }

        @Override
        void addToBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToBalanceNQT(event, eventId, amount);
        }

        @Override
        void addToUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToUnconfirmedBalanceNQT(event, eventId, amount);
        }

        @Override
        void addToBalanceAndUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToBalanceAndUnconfirmedBalanceNQT(event, eventId, amount);
        }

    },

    ASSET((byte) 1) {
        @Override
        public long getUnconfirmedBalance(Account account, long holdingId) {
            return account.getUnconfirmedPropertyBalanceQNT(holdingId);
        }

        @Override
        void addToBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToAssetBalanceQNT(event, eventId, holdingId, amount);
        }

        @Override
        void addToUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToUnconfirmedAssetBalanceQNT(event, eventId, holdingId, amount);
        }

        @Override
        void addToBalanceAndUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToAssetAndUnconfirmedAssetBalanceQNT(event, eventId, holdingId, amount);
        }

    },

    CURRENCY((byte) 2) {
        @Override
        public long getUnconfirmedBalance(Account account, long holdingId) {
            return account.getUnconfirmedCoinUnits(holdingId);
        }

        @Override
        void addToBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToCurrencyUnits(event, eventId, holdingId, amount);
        }

        @Override
        void addToUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToUnconfirmedCurrencyUnits(event, eventId, holdingId, amount);
        }

        @Override
        void addToBalanceAndUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount) {
            account.addToCurrencyAndUnconfirmedCurrencyUnits(event, eventId, holdingId, amount);
        }

    };

    private final byte code;

    HoldingType(byte code) {
        this.code = code;
    }

    public static HoldingType get(byte code) {
        for (HoldingType holdingType : values()) {
            if (holdingType.getCode() == code) {
                return holdingType;
            }
        }
        throw new IllegalArgumentException("Invalid holdingType code: " + code);
    }

    public byte getCode() {
        return code;
    }

    public abstract long getUnconfirmedBalance(Account account, long holdingId);

    abstract void addToBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount);

    abstract void addToUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount);

    abstract void addToBalanceAndUnconfirmedBalance(Account account, LedgerEvent event, long eventId, long holdingId, long amount);

}
