package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllWaitingTransactions extends APIRequestHandler {

    static final GetAllWaitingTransactions instance = new GetAllWaitingTransactions();

    private GetAllWaitingTransactions() {
        super(new APITag[]{APITag.DEBUG});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        response.put("transactions", jsonArray);
        Transaction[] transactions = TransactionProcessorImpl.getInstance().getAllWaitingTransactions();
        for (Transaction transaction : transactions) {
            jsonArray.add(JSONData.unconfirmedTransaction(transaction));
        }
        return response;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
