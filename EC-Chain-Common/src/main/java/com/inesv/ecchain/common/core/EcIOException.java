package com.inesv.ecchain.common.core;

import java.io.IOException;

public final class EcIOException extends IOException {

    public EcIOException(String message) {
        super(message);
    }

    public EcIOException(String message, Throwable cause) {
        super(message, cause);
    }

}
