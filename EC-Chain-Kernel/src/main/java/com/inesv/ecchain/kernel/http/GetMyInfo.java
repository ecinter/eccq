package com.inesv.ecchain.kernel.http;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetMyInfo extends APIRequestHandler {

    static final GetMyInfo instance = new GetMyInfo();

    private GetMyInfo() {
        super(new APITag[]{APITag.NETWORK});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = new JSONObject();
        response.put("host", req.getRemoteHost());
        response.put("address", req.getRemoteAddr());
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
