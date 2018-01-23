package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.PRUNED_TRANSACTION;
import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_TRANSACTION;


public class RetrievePrunedTransaction extends APIRequestHandler {

    static final RetrievePrunedTransaction instance = new RetrievePrunedTransaction();

    private RetrievePrunedTransaction() {
        super(new APITag[]{APITag.TRANSACTIONS}, "transaction");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        Transaction transaction = EcBlockchainImpl.getInstance().getTransaction(transactionId);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }
        transaction = EcBlockchainProcessorImpl.getInstance().restorePrunedTransaction(transactionId);
        if (transaction == null) {
            return PRUNED_TRANSACTION;
        }
        return JSONData.transaction(transaction);
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

}
