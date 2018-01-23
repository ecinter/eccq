package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Coin;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllCoin extends APIRequestHandler {

    static final GetAllCoin instance = new GetAllCoin();

    private GetAllCoin() {
        super(new APITag[]{APITag.MS}, "firstIndex", "lastIndex", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));

        JSONObject response = new JSONObject();
        JSONArray currenciesJSONArray = new JSONArray();
        response.put("currencies", currenciesJSONArray);
        try (H2Iterator<Coin> currencies = Coin.getAllCurrencies(firstIndex, lastIndex)) {
            for (Coin coin : currencies) {
                currenciesJSONArray.add(JSONData.currency(coin, includeCounts));
            }
        }
        return response;
    }

}
