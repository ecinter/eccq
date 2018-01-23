package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Shuffler;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class StopShuffler extends APIRequestHandler {

    static final StopShuffler instance = new StopShuffler();

    private StopShuffler() {
        super(new APITag[]{APITag.SHUFFLING}, "account", "shufflingFullHash", "secretPhrase", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        byte[] shufflingFullHash = ParameterParser.getBytes(req, "shufflingFullHash", false);
        long accountId = ParameterParser.getAccountId(req, false);
        JSONObject response = new JSONObject();
        if (secretPhrase != null) {
            if (accountId != 0 && Account.getId(Crypto.getPublicKey(secretPhrase)) != accountId) {
                return JSONResponses.INCORRECT_ACCOUNT;
            }
            accountId = Account.getId(Crypto.getPublicKey(secretPhrase));
            if (shufflingFullHash.length == 0) {
                return JSONResponses.missing("shufflingFullHash");
            }
            Shuffler shuffler = Shuffler.stopShuffler(accountId, shufflingFullHash);
            response.put("stoppedShuffler", shuffler != null);
        } else {
            API.verifyPassword(req);
            if (accountId != 0 && shufflingFullHash.length != 0) {
                Shuffler shuffler = Shuffler.stopShuffler(accountId, shufflingFullHash);
                response.put("stoppedShuffler", shuffler != null);
            } else if (accountId == 0 && shufflingFullHash.length == 0) {
                Shuffler.stopAllShufflers();
                response.put("stoppedAllShufflers", true);
            } else if (accountId != 0) {
                return JSONResponses.missing("shufflingFullHash");
            } else if (shufflingFullHash.length != 0) {
                return JSONResponses.missing("account");
            }
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
