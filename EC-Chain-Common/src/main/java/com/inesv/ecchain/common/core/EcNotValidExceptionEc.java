package com.inesv.ecchain.common.core;


public final class EcNotValidExceptionEc extends EcValidationException {

    public EcNotValidExceptionEc(String message) {
        super(message);
    }

    public EcNotValidExceptionEc(String message, Throwable cause) {
        super(message, cause);
    }

}
