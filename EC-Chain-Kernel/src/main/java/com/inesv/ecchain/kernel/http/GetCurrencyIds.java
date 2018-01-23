package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Coin;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCurrencyIds extends APIRequestHandler {

    static final GetCurrencyIds instance = new GetCurrencyIds();

    private GetCurrencyIds() {
        super(new APITag[]{APITag.MS}, "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray currencyIds = new JSONArray();
        try (H2Iterator<Coin> currencies = Coin.getAllCurrencies(firstIndex, lastIndex)) {
            for (Coin coin : currencies) {
                currencyIds.add(Long.toUnsignedString(coin.getId()));
            }
        }
        JSONObject response = new JSONObject();
        response.put("currencyIds", currencyIds);
        return response;
    }

}
