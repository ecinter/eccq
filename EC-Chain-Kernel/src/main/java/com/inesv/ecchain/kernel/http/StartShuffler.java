package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.Shuffler;
import com.inesv.ecchain.kernel.core.Shuffling;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class StartShuffler extends APIRequestHandler {

    static final StartShuffler instance = new StartShuffler();

    private StartShuffler() {
        super(new APITag[]{APITag.SHUFFLING}, "secretPhrase", "shufflingFullHash", "recipientSecretPhrase", "recipientPublicKey");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        byte[] shufflingFullHash = ParameterParser.getBytes(req, "shufflingFullHash", true);
        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        byte[] recipientPublicKey = ParameterParser.getPublicKey(req, "recipient");
        try {
            Shuffler shuffler = Shuffler.addOrGetShuffler(secretPhrase, recipientPublicKey, shufflingFullHash);
            return shuffler != null ? JSONData.shuffler(shuffler, false) : JSON.EMPTY_JSON;
        } catch (Shuffler.ShufflerLimitException e) {
            JSONObject response = new JSONObject();
            response.put("errorCode", 7);
            response.put("errorDescription", e.getMessage());
            return JSON.prepare(response);
        } catch (Shuffler.DuplicateShufflerException e) {
            JSONObject response = new JSONObject();
            response.put("errorCode", 8);
            response.put("errorDescription", e.getMessage());
            return JSON.prepare(response);
        } catch (Shuffler.InvalidRecipientException e) {
            return JSONResponses.incorrect("recipientPublicKey", e.getMessage());
        } catch (Shuffler.ControlledAccountException e) {
            JSONObject response = new JSONObject();
            response.put("errorCode", 9);
            response.put("errorDescription", e.getMessage());
            return JSON.prepare(response);
        } catch (Shuffler.ShufflerException e) {
            if (e.getCause() instanceof EcInsufficientBalanceExceptionEcEc) {
                Shuffling shuffling = Shuffling.getShuffling(shufflingFullHash);
                if (shuffling == null) {
                    return JSONResponses.NOT_ENOUGH_FUNDS;
                }
                return JSONResponses.notEnoughHolding(shuffling.getHoldingType());
            }
            JSONObject response = new JSONObject();
            response.put("errorCode", 10);
            response.put("errorDescription", e.getMessage());
            return JSON.prepare(response);
        }
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
