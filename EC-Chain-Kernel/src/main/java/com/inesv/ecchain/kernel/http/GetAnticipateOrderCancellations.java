package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.ColoredCoins;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetAnticipateOrderCancellations extends APIRequestHandler {

    static final GetAnticipateOrderCancellations instance = new GetAnticipateOrderCancellations();

    private GetAnticipateOrderCancellations() {
        super(new APITag[]{APITag.AE});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Filter<Transaction> filter = transaction -> transaction.getTransactionType() == ColoredCoins.ASK_ORDER_CANCELLATION
                || transaction.getTransactionType() == ColoredCoins.BID_ORDER_CANCELLATION;

        List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);
        JSONArray cancellations = new JSONArray();
        transactions.forEach(transaction -> cancellations.add(JSONData.expectedOrderCancellation(transaction)));
        JSONObject response = new JSONObject();
        response.put("orderCancellations", cancellations);
        return response;
    }
}
