package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetSharedKey extends APIRequestHandler {

    static final GetSharedKey instance = new GetSharedKey();

    private GetSharedKey() {
        super(new APITag[]{APITag.MESSAGES}, "account", "secretPhrase", "nonce");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        byte[] nonce = ParameterParser.getBytes(req, "nonce", true);
        long accountId = ParameterParser.getAccountId(req, "account", true);
        byte[] publicKey = Account.getPublicKey(accountId);
        if (publicKey == null) {
            return JSONResponses.INCORRECT_ACCOUNT;
        }
        byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), publicKey, nonce);
        JSONObject response = new JSONObject();
        response.put("sharedKey", Convert.toHexString(sharedKey));
        return response;

    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
