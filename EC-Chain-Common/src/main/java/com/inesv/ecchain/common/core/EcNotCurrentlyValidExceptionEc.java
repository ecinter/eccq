package com.inesv.ecchain.common.core;

public class EcNotCurrentlyValidExceptionEc extends EcValidationException {

    public EcNotCurrentlyValidExceptionEc(String message) {
        super(message);
    }

    public EcNotCurrentlyValidExceptionEc(String message, Throwable cause) {
        super(message, cause);
    }

}
