package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Coin;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCurrenciesByIssuer extends APIRequestHandler {

    static final GetCurrenciesByIssuer instance = new GetCurrenciesByIssuer();

    private GetCurrenciesByIssuer() {
        super(new APITag[]{APITag.MS, APITag.ACCOUNTS}, "account", "account", "account", "firstIndex", "lastIndex", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long[] accountIds = ParameterParser.getAccountIds(req, true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));

        JSONObject response = new JSONObject();
        JSONArray accountsJSONArray = new JSONArray();
        response.put("currencies", accountsJSONArray);
        for (long accountId : accountIds) {
            JSONArray currenciesJSONArray = new JSONArray();
            try (H2Iterator<Coin> currencies = Coin.getCoinIssuedBy(accountId, firstIndex, lastIndex)) {
                for (Coin coin : currencies) {
                    currenciesJSONArray.add(JSONData.currency(coin, includeCounts));
                }
            }
            accountsJSONArray.add(currenciesJSONArray);
        }
        return response;
    }

}
