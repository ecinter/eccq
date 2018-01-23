package com.inesv.ecchain.kernel.core;

public class Mint {

    public final long accountId;
    public final long currencyId;
    public final long units;

    Mint(long accountId, long currencyId, long units) {
        this.accountId = accountId;
        this.currencyId = currencyId;
        this.units = units;
    }

}
