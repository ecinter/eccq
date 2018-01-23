package com.inesv.ecchain.kernel.H2;

public enum H2ClauseOp {

    LT("<"), LTE("<="), GT(">"), GTE(">="), NE("<>");

    private final String operator;

    H2ClauseOp(String operator) {
        this.operator = operator;
    }

    public String operator() {
        return operator;
    }
}
