package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_CURRENCY;

public final class GetCurrencies extends APIRequestHandler {

    static final GetCurrencies instance = new GetCurrencies();

    private GetCurrencies() {
        super(new APITag[]{APITag.MS}, "currencies", "currencies", "currencies", "includeCounts"); // limit to 3 for testing
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long[] currencyIds = ParameterParser.getUnsignedLongs(req, "currencies");
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));
        JSONObject response = new JSONObject();
        JSONArray currenciesJSONArray = new JSONArray();
        response.put("currencies", currenciesJSONArray);
        for (long currencyId : currencyIds) {
            Coin coin = Coin.getCoin(currencyId);
            if (coin == null) {
                return UNKNOWN_CURRENCY;
            }
            currenciesJSONArray.add(JSONData.currency(coin, includeCounts));
        }
        return response;
    }

}
