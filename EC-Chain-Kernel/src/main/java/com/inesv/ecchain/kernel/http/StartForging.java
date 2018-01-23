package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.FoundryMachine;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class StartForging extends APIRequestHandler {

    static final StartForging instance = new StartForging();

    private StartForging() {
        super(new APITag[]{APITag.FORGING}, "secretPhrase");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        FoundryMachine foundryMachine = FoundryMachine.startForging(secretPhrase);
        JSONObject response = new JSONObject();
        response.put("deadline", foundryMachine.getDeadline());
        response.put("hitTime", foundryMachine.getHitTime());
        return response;

    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireFullClient() {
        return true;
    }

}
