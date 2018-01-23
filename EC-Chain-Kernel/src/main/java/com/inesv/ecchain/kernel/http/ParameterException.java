package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import org.json.simple.JSONStreamAware;

public final class ParameterException extends EcException {

    private final JSONStreamAware errorResponse;

    ParameterException(JSONStreamAware errorResponse) {
        this.errorResponse = errorResponse;
    }

    JSONStreamAware getErrorResponse() {
        return errorResponse;
    }

}
