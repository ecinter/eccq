package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Shuffler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;


public final class GetShufflers extends APIRequestHandler {

    static final GetShufflers instance = new GetShufflers();

    private GetShufflers() {
        super(new APITag[]{APITag.SHUFFLING}, "account", "shufflingFullHash", "secretPhrase", "adminPassword", "includeParticipantState");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        byte[] shufflingFullHash = ParameterParser.getBytes(req, "shufflingFullHash", false);
        long accountId = ParameterParser.getAccountId(req, false);
        boolean includeParticipantState = "true".equalsIgnoreCase(req.getParameter("includeParticipantState"));
        List<Shuffler> shufflers;
        if (secretPhrase != null) {
            if (accountId != 0 && Account.getId(Crypto.getPublicKey(secretPhrase)) != accountId) {
                return JSONResponses.INCORRECT_ACCOUNT;
            }
            accountId = Account.getId(Crypto.getPublicKey(secretPhrase));
            if (shufflingFullHash.length == 0) {
                shufflers = Shuffler.getAccountShufflers(accountId);
            } else {
                Shuffler shuffler = Shuffler.getShuffler(accountId, shufflingFullHash);
                shufflers = shuffler == null ? Collections.emptyList() : Collections.singletonList(shuffler);
            }
        } else {
            API.verifyPassword(req);
            if (accountId != 0 && shufflingFullHash.length == 0) {
                shufflers = Shuffler.getAccountShufflers(accountId);
            } else if (accountId == 0 && shufflingFullHash.length > 0) {
                shufflers = Shuffler.getShufflingShufflers(shufflingFullHash);
            } else if (accountId != 0 && shufflingFullHash.length > 0) {
                Shuffler shuffler = Shuffler.getShuffler(accountId, shufflingFullHash);
                shufflers = shuffler == null ? Collections.emptyList() : Collections.singletonList(shuffler);
            } else {
                shufflers = Shuffler.getAllShufflers();
            }
        }
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        shufflers.forEach(shuffler -> jsonArray.add(JSONData.shuffler(shuffler, includeParticipantState)));
        response.put("shufflers", jsonArray);
        return response;
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
