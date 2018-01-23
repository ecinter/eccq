package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

public final class GetAnticipateTransactions extends APIRequestHandler {

    static final GetAnticipateTransactions instance = new GetAnticipateTransactions();

    private GetAnticipateTransactions() {
        super(new APITag[]{APITag.TRANSACTIONS}, "account", "account", "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
        Filter<Transaction> filter = accountIds.isEmpty() ? transaction -> true :
                transaction -> accountIds.contains(transaction.getSenderId()) || accountIds.contains(transaction.getRecipientId());

        List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        transactions.forEach(transaction -> jsonArray.add(JSONData.unconfirmedTransaction(transaction)));
        response.put("expectedTransactions", jsonArray);

        return response;
    }

}
