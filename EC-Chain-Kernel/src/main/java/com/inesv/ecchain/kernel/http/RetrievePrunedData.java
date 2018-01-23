package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public class RetrievePrunedData extends APIRequestHandler {

    static final RetrievePrunedData instance = new RetrievePrunedData();

    private RetrievePrunedData() {
        super(new APITag[]{APITag.DEBUG});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        try {
            int count = EcBlockchainProcessorImpl.getInstance().restorePrunedData();
            response.put("done", true);
            response.put("numberOfPrunedData", count);
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
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

}
