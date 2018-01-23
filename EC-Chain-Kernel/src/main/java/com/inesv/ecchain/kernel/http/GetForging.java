package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.EcBlockchain;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.NOT_FORGING;
import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_ACCOUNT;


public final class GetForging extends APIRequestHandler {

    static final GetForging instance = new GetForging();

    private GetForging() {
        super(new APITag[]{APITag.FORGING}, "secretPhrase", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        EcBlockchain bc = EcBlockchainImpl.getInstance();
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        int elapsedTime = new EcTime.EpochEcTime().getTime() - bc.getLastECBlock().getTimestamp();
        if (secretPhrase != null) {
            Account account = Account.getAccount(Crypto.getPublicKey(secretPhrase));
            if (account == null) {
                return UNKNOWN_ACCOUNT;
            }
            FoundryMachine foundryMachine = FoundryMachine.getFoundryMachine(secretPhrase);
            if (foundryMachine == null) {
                return NOT_FORGING;
            }
            return JSONData.generator(foundryMachine, elapsedTime);
        } else {
            API.verifyPassword(req);
            JSONObject response = new JSONObject();
            JSONArray generators = new JSONArray();
            FoundryMachine.getSortedForgers().forEach(generator -> generators.add(JSONData.generator(generator, elapsedTime)));
            response.put("generators", generators);
            return response;
        }
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
