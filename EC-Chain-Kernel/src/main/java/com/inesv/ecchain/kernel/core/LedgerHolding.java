package com.inesv.ecchain.kernel.core;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

public enum LedgerHolding {
    UNCONFIRMED_EC_BALANCE(1, true),
    EC_BALANCE(2, false),
    UNCONFIRMED_ASSET_BALANCE(3, true),
    ASSET_BALANCE(4, false),
    UNCONFIRMED_CURRENCY_BALANCE(5, true),
    CURRENCY_BALANCE(6, false);

    /**
     * Holding code mapping
     */
    private static final Map<Integer, LedgerHolding> holdingMap = new HashMap<>();

    @PostConstruct
    public static void initPostConstruct() {
        for (LedgerHolding holding : values()) {
            if (holdingMap.put(holding.code, holding) != null) {
                throw new RuntimeException("LedgerHolding code " + holding.code + " reused");
            }
        }
    }

    /**
     * Holding code
     */
    private final int code;

    /**
     * Unconfirmed holding
     */
    private final boolean isUnconfirmed;

    /**
     * Create the holding event
     *
     * @param code          Holding code
     * @param isUnconfirmed TRUE if the holding is unconfirmed
     */
    LedgerHolding(int code, boolean isUnconfirmed) {
        this.code = code;
        this.isUnconfirmed = isUnconfirmed;
    }

    /**
     * Get the holding from the holding code
     *
     * @param code Holding code
     * @return Holding
     */
    public static LedgerHolding fromCode(int code) {
        LedgerHolding holding = holdingMap.get(code);
        if (holding == null) {
            throw new IllegalArgumentException("LedgerHolding code " + code + " is unknown");
        }
        return holding;
    }

    /**
     * Check if the holding is unconfirmed
     *
     * @return TRUE if the holding is unconfirmed
     */
    public boolean isUnconfirmed() {
        return this.isUnconfirmed;
    }

    /**
     * Return the holding code
     *
     * @return Holding code
     */
    public int getCode() {
        return code;
    }
}
