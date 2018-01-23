package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Poll;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class GetVote extends APIRequestHandler {

    static final GetVote instance = new GetVote();

    private GetVote() {
        super(new APITag[]{APITag.VS}, "poll");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Poll poll = ParameterParser.getPoll(req);
        return JSONData.poll(poll);
    }
}
