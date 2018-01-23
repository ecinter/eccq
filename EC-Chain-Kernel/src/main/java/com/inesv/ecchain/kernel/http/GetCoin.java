package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCoin extends APIRequestHandler {

    static final GetCoin instance = new GetCoin();

    private GetCoin() {
        super(new APITag[]{APITag.AE}, "asset", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));
        return JSONData.asset(ParameterParser.getAsset(req), includeCounts);
    }

}
