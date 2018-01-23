package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetReferencingTransactions extends APIRequestHandler {

    static final GetReferencingTransactions instance = new GetReferencingTransactions();

    private GetReferencingTransactions() {
        super(new APITag[]{APITag.TRANSACTIONS}, "transaction", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray transactions = new JSONArray();
        try (H2Iterator<? extends Transaction> iterator = EcBlockchainImpl.getInstance().getReferencingTransactions(transactionId, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                Transaction transaction = iterator.next();
                transactions.add(JSONData.transaction(transaction));
            }
        }

        JSONObject response = new JSONObject();
        response.put("transactions", transactions);
        return response;

    }

}
