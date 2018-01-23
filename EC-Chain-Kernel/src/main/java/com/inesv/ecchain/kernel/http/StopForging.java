package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.FoundryMachine;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class StopForging extends APIRequestHandler {

    static final StopForging instance = new StopForging();

    private StopForging() {
        super(new APITag[]{APITag.FORGING}, "secretPhrase", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        JSONObject response = new JSONObject();
        if (secretPhrase != null) {
            FoundryMachine foundryMachine = FoundryMachine.stopForging(secretPhrase);
            response.put("foundAndStopped", foundryMachine != null);
            response.put("forgersCount", FoundryMachine.getFoundryMachineCount());
        } else {
            API.verifyPassword(req);
            int count = FoundryMachine.stopForging();
            response.put("stopped", count);
        }
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
