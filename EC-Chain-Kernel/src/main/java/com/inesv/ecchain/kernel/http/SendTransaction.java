package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Builder;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

public final class SendTransaction extends APIRequestHandler {

    static final SendTransaction instance = new SendTransaction();

    private SendTransaction() {
        super(new APITag[]{APITag.TRANSACTIONS}, "transactionJSON", "transactionBytes", "prunableAttachmentJSON");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String transactionJSON = Convert.emptyToNull(req.getParameter("transactionJSON"));
        String transactionBytes = Convert.emptyToNull(req.getParameter("transactionBytes"));
        String prunableAttachmentJSON = Convert.emptyToNull(req.getParameter("prunableAttachmentJSON"));

        JSONObject response = new JSONObject();
        try {
            Builder builder = ParameterParser.parseTransaction(transactionJSON, transactionBytes, prunableAttachmentJSON);
            Transaction transaction = builder.build();
            Peers.sendToSomePeers(Collections.singletonList(transaction));
            response.put("transaction", transaction.getStringId());
            response.put("fullHash", transaction.getFullHash());
        } catch (EcValidationException | RuntimeException e) {
            JSONData.putException(response, e, "Failed to broadcast transaction");
        }
        return response;

    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

}
