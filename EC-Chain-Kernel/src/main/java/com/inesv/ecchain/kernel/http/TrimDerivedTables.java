package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class TrimDerivedTables extends APIRequestHandler {

    static final TrimDerivedTables instance = new TrimDerivedTables();

    private TrimDerivedTables() {
        super(new APITag[]{APITag.DEBUG});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        EcBlockchainProcessorImpl.getInstance().trimDerivedTables();
        response.put("done", true);
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
