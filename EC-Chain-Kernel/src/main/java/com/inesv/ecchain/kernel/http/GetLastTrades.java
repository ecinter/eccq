package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Trade;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetLastTrades extends APIRequestHandler {

    static final GetLastTrades instance = new GetLastTrades();

    private GetLastTrades() {
        super(new APITag[]{APITag.AE}, "assets", "assets", "assets"); // limit to 3 for testing
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long[] assetIds = ParameterParser.getUnsignedLongs(req, "assets");
        JSONArray tradesJSON = new JSONArray();
        List<Trade> trades = Trade.getLastTrades(assetIds);
        trades.forEach(trade -> tradesJSON.add(JSONData.trade(trade, false)));
        JSONObject response = new JSONObject();
        response.put("trades", tradesJSON);
        return response;
    }

}
