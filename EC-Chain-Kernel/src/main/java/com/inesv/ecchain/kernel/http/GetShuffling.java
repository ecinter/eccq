package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetShuffling extends APIRequestHandler {

    static final GetShuffling instance = new GetShuffling();

    private GetShuffling() {
        super(new APITag[]{APITag.SHUFFLING}, "shuffling", "includeHoldingInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        boolean includeHoldingInfo = "true".equalsIgnoreCase(req.getParameter("includeHoldingInfo"));
        return JSONData.shuffling(ParameterParser.getShuffling(req), includeHoldingInfo);
    }

}
