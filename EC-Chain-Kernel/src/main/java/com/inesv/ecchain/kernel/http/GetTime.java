package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.EcTime;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetTime extends APIRequestHandler {

    static final GetTime instance = new GetTime();

    private GetTime() {
        super(new APITag[]{APITag.INFO});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = new JSONObject();
        response.put("time", new EcTime.EpochEcTime().getTime());

        return response;
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
