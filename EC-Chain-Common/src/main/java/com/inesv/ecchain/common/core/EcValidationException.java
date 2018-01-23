package com.inesv.ecchain.common.core;


public abstract class EcValidationException extends EcException {

    public EcValidationException(String message) {
        super(message);
    }

    public EcValidationException(String message, Throwable cause) {
        super(message, cause);
    }

}
