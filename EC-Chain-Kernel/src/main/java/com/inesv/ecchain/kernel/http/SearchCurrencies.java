package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Coin;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SearchCurrencies extends APIRequestHandler {

    static final SearchCurrencies instance = new SearchCurrencies();

    private SearchCurrencies() {
        super(new APITag[]{APITag.MS, APITag.SEARCH}, "query", "firstIndex", "lastIndex", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        String query = Convert.nullToEmpty(req.getParameter("query"));
        if (query.isEmpty()) {
            return JSONResponses.missing("query");
        }
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        try (H2Iterator<Coin> currencies = Coin.searchCoins(query, firstIndex, lastIndex)) {
            while (currencies.hasNext()) {
                jsonArray.add(JSONData.currency(currencies.next(), includeCounts));
            }
        }
        response.put("currencies", jsonArray);
        return response;
    }

}
