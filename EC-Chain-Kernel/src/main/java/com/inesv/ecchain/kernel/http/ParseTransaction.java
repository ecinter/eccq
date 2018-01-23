package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class ParseTransaction extends APIRequestHandler {

    static final ParseTransaction instance = new ParseTransaction();

    private ParseTransaction() {
        super(new APITag[]{APITag.TRANSACTIONS}, "transactionJSON", "transactionBytes", "prunableAttachmentJSON");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        String transactionBytes = Convert.emptyToNull(req.getParameter("transactionBytes"));
        String transactionJSON = Convert.emptyToNull(req.getParameter("transactionJSON"));
        String prunableAttachmentJSON = Convert.emptyToNull(req.getParameter("prunableAttachmentJSON"));

        Transaction transaction = ParameterParser.parseTransaction(transactionJSON, transactionBytes, prunableAttachmentJSON).build();
        JSONObject response = JSONData.unconfirmedTransaction(transaction);
        try {
            transaction.validate();
        } catch (EcValidationException | RuntimeException e) {
            LoggerUtil.logError(e.getMessage(), e);
            response.put("validate", false);
            JSONData.putException(response, e, "Invalid transaction");
        }
        response.put("ecVerify", transaction.verifySignature());
        return response;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
