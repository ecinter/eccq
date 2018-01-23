
package com.inesv.ecchain.common.core;

public abstract class EcException extends Exception {

    protected EcException() {
        super();
    }

    protected EcException(String message) {
        super(message);
    }

    protected EcException(String message, Throwable cause) {
        super(message, cause);
    }

    protected EcException(Throwable cause) {
        super(cause);
    }
}
