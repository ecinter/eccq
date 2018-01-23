package com.inesv.ecchain.common.core;

public class EcInsufficientBalanceExceptionEcEc extends EcNotCurrentlyValidExceptionEc {

    public EcInsufficientBalanceExceptionEcEc(String message) {
        super(message);
    }

    public EcInsufficientBalanceExceptionEcEc(String message, Throwable cause) {
        super(message, cause);
    }

}
