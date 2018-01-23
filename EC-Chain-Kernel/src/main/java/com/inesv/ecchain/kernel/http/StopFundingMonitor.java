package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.FundMonitoring;
import com.inesv.ecchain.kernel.core.HoldingType;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public class StopFundingMonitor extends APIRequestHandler {

    static final StopFundingMonitor instance = new StopFundingMonitor();

    private StopFundingMonitor() {
        super(new APITag[]{APITag.ACCOUNTS}, "holdingType", "holding", "property", "secretPhrase",
                "account", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        long accountId = ParameterParser.getAccountId(req, false);
        JSONObject response = new JSONObject();
        if (secretPhrase == null) {
            API.verifyPassword(req);
        }
        if (secretPhrase != null || accountId != 0) {
            if (secretPhrase != null) {
                if (accountId != 0) {
                    if (Account.getId(Crypto.getPublicKey(secretPhrase)) != accountId) {
                        return JSONResponses.INCORRECT_ACCOUNT;
                    }
                } else {
                    accountId = Account.getId(Crypto.getPublicKey(secretPhrase));
                }
            }
            HoldingType holdingType = ParameterParser.getHoldingType(req);
            long holdingId = ParameterParser.getHoldingId(req, holdingType);
            String property = ParameterParser.getAccountProperty(req, true);
            boolean stopped = FundMonitoring.stopMonitor(holdingType, holdingId, property, accountId);
            response.put("stopped", stopped ? 1 : 0);
        } else {
            int count = FundMonitoring.stopAllMonitors();
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
