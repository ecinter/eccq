package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.kernel.http.APIRequestHandler;

public interface PlugIn {

    default void init() {
    }

    default void shutdown() {
    }

    default APIRequestHandler getAPIRequestHandler() {
        return null;
    }

    default String getAPIRequestType() {
        return null;
    }

}
