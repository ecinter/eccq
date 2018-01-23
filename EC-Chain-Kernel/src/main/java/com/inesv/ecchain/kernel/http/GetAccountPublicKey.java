package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.Account;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountPublicKey extends APIRequestHandler {

    static final GetAccountPublicKey instance = new GetAccountPublicKey();

    private GetAccountPublicKey() {
        super(new APITag[]{APITag.ACCOUNTS}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        byte[] publicKey = Account.getPublicKey(accountId);
        if (publicKey != null) {
            JSONObject response = new JSONObject();
            response.put("publicKey", Convert.toHexString(publicKey));
            return response;
        } else {
            return JSON.EMPTY_JSON;
        }
    }

}
