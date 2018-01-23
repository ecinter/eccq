package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Trade;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllTrades extends APIRequestHandler {

    static final GetAllTrades instance = new GetAllTrades();

    private GetAllTrades() {
        super(new APITag[]{APITag.AE}, "timestamp", "firstIndex", "lastIndex", "includeAssetInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        final int timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeAssetInfo = "true".equalsIgnoreCase(req.getParameter("includeAssetInfo"));

        JSONObject response = new JSONObject();
        JSONArray trades = new JSONArray();
        try (H2Iterator<Trade> tradeIterator = Trade.getAllTrades(firstIndex, lastIndex)) {
            while (tradeIterator.hasNext()) {
                Trade trade = tradeIterator.next();
                if (trade.getTimestamp() < timestamp) {
                    break;
                }
                trades.add(JSONData.trade(trade, includeAssetInfo));
            }
        }
        response.put("trades", trades);
        return response;
    }

}
