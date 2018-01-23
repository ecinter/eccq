package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.PhasingPoll;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.core.VoteWeighting;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetCurrencyPhasedTransactions extends APIRequestHandler {
    static final GetCurrencyPhasedTransactions instance = new GetCurrencyPhasedTransactions();

    private GetCurrencyPhasedTransactions() {
        super(new APITag[]{APITag.MS, APITag.PHASING}, "currency", "account", "withoutWhitelist", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long currencyId = ParameterParser.getUnsignedLong(req, "currency", true);
        long accountId = ParameterParser.getAccountId(req, false);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean withoutWhitelist = "true".equalsIgnoreCase(req.getParameter("withoutWhitelist"));

        JSONArray transactions = new JSONArray();
        try (H2Iterator<? extends Transaction> iterator = PhasingPoll.getHoldingPhasedTransactions(currencyId, VoteWeighting.VotingModel.CURRENCY,
                accountId, withoutWhitelist, firstIndex, lastIndex)) {
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
