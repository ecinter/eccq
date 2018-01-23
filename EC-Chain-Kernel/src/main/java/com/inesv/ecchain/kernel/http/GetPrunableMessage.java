package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import com.inesv.ecchain.kernel.core.PrunableMessage;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.PRUNED_TRANSACTION;

public final class GetPrunableMessage extends APIRequestHandler {

    static final GetPrunableMessage instance = new GetPrunableMessage();

    private GetPrunableMessage() {
        super(new APITag[]{APITag.MESSAGES}, "transaction", "secretPhrase", "sharedKey", "retrieve");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        byte[] sharedKey = ParameterParser.getBytes(req, "sharedKey", false);
        if (sharedKey.length != 0 && secretPhrase != null) {
            return JSONResponses.either("secretPhrase", "sharedKey");
        }
        boolean retrieve = "true".equalsIgnoreCase(req.getParameter("retrieve"));
        PrunableMessage prunableMessage = PrunableMessage.getPrunableMessage(transactionId);
        if (prunableMessage == null && retrieve) {
            if (EcBlockchainProcessorImpl.getInstance().restorePrunedTransaction(transactionId) == null) {
                return PRUNED_TRANSACTION;
            }
            prunableMessage = PrunableMessage.getPrunableMessage(transactionId);
        }
        if (prunableMessage != null) {
            return JSONData.prunableMessage(prunableMessage, secretPhrase, sharedKey);
        }
        return JSON.EMPTY_JSON;
    }

}
