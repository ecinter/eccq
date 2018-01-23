package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ACCOUNT;
import static com.inesv.ecchain.kernel.http.JSONResponses.MISSING_ACCOUNT;


public final class RSConvert extends APIRequestHandler {

    static final RSConvert instance = new RSConvert();

    private RSConvert() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.UTILS}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        String accountValue = Convert.emptyToNull(req.getParameter("account"));
        if (accountValue == null) {
            return MISSING_ACCOUNT;
        }
        try {
            long accountId = Convert.parseAccountId(accountValue);
            if (accountId == 0) {
                return INCORRECT_ACCOUNT;
            }
            JSONObject response = new JSONObject();
            JSONData.putAccount(response, "account", accountId);
            return response;
        } catch (RuntimeException e) {
            return INCORRECT_ACCOUNT;
        }
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
