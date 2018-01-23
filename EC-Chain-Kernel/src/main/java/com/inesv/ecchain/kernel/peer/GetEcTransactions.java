package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.kernel.core.EcBlockchain;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

/**
 * Get the transactions
 */
public class GetEcTransactions extends PeerRequestHandler {

    static final GetEcTransactions instance = new GetEcTransactions();

    private GetEcTransactions() {
    }

    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {
        if (!Constants.INCLUDE_EXPIRED_PRUNABLE) {
            return PeerServlet.UNSUPPORTED_REQUEST_TYPE;
        }
        JSONObject response = new JSONObject();
        JSONArray transactionArray = new JSONArray();
        JSONArray transactionIds = (JSONArray) request.get("transactionIds");
        EcBlockchain ecBlockchain = EcBlockchainImpl.getInstance();
        //
        // Return the transactions to the caller
        //
        if (transactionIds != null) {
            transactionIds.forEach(transactionId -> {
                long id = Long.parseUnsignedLong((String) transactionId);
                Transaction transaction = ecBlockchain.getTransaction(id);
                if (transaction != null) {
                    transaction.getAppendages(true);
                    JSONObject transactionJSON = transaction.getJSONObject();
                    transactionArray.add(transactionJSON);
                }
            });
        }
        response.put("transactions", transactionArray);
        return response;
    }

    @Override
    boolean rejectRequest() {
        return true;
    }
}
