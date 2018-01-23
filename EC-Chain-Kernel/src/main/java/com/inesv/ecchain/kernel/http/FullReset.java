package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class FullReset extends APIRequestHandler {

    static final FullReset instance = new FullReset();

    private FullReset() {
        super(new APITag[]{APITag.DEBUG});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        try {
            EcBlockchainProcessorImpl.getInstance().fullReset();
            response.put("done", true);
        } catch (RuntimeException e) {
            JSONData.putException(response, e);
        }
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
