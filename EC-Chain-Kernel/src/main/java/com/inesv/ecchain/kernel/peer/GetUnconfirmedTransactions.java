package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.List;
import java.util.SortedSet;

final class GetUnconfirmedTransactions extends PeerRequestHandler {

    static final GetUnconfirmedTransactions instance = new GetUnconfirmedTransactions();

    private GetUnconfirmedTransactions() {
    }


    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {

        List<String> exclude = (List<String>) request.get("exclude");
        if (exclude == null) {
            return JSON.EMPTY_JSON;
        }

        SortedSet<? extends Transaction> transactionSet = TransactionProcessorImpl.getInstance().getCachedUnconfirmedTransactions(exclude);
        JSONArray transactionsData = new JSONArray();
        for (Transaction transaction : transactionSet) {
            if (transactionsData.size() >= 100) {
                break;
            }
            transactionsData.add(transaction.getJSONObject());
        }
        JSONObject response = new JSONObject();
        response.put("unconfirmedTransactions", transactionsData);

        return response;
    }

    @Override
    boolean rejectRequest() {
        return true;
    }

}
