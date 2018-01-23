package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class GetTransactionBytes extends APIRequestHandler {

    static final GetTransactionBytes instance = new GetTransactionBytes();

    private GetTransactionBytes() {
        super(new APITag[]{APITag.TRANSACTIONS}, "transaction");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        String transactionValue = req.getParameter("transaction");
        if (transactionValue == null) {
            return MISSING_TRANSACTION;
        }

        long transactionId;
        Transaction transaction;
        try {
            transactionId = Convert.parseUnsignedLong(transactionValue);
        } catch (RuntimeException e) {
            return INCORRECT_TRANSACTION;
        }

        transaction = EcBlockchainImpl.getInstance().getTransaction(transactionId);
        JSONObject response = new JSONObject();
        if (transaction == null) {
            transaction = TransactionProcessorImpl.getInstance().getUnconfirmedTransaction(transactionId);
            if (transaction == null) {
                return UNKNOWN_TRANSACTION;
            }
        } else {
            response.put("confirmations", EcBlockchainImpl.getInstance().getHeight() - transaction.getTransactionHeight());
        }
        response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
        response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
        JSONData.putPrunableAttachment(response, transaction);
        return response;

    }

}
