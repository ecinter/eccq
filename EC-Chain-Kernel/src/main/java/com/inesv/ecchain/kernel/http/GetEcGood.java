package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcGood extends APIRequestHandler {

    static final GetEcGood instance = new GetEcGood();

    private GetEcGood() {
        super(new APITag[]{APITag.DGS}, "goods", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));
        return JSONData.goods(ParameterParser.getGoods(req), includeCounts);
    }

}
