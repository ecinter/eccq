package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Trade;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetOrderTrades extends APIRequestHandler {

    static final GetOrderTrades instance = new GetOrderTrades();

    private GetOrderTrades() {
        super(new APITag[]{APITag.AE}, "askOrder", "bidOrder", "includeAssetInfo", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long askOrderId = ParameterParser.getUnsignedLong(req, "askOrder", false);
        long bidOrderId = ParameterParser.getUnsignedLong(req, "bidOrder", false);
        boolean includeAssetInfo = "true".equalsIgnoreCase(req.getParameter("includeAssetInfo"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        if (askOrderId == 0 && bidOrderId == 0) {
            return JSONResponses.missing("askOrder", "bidOrder");
        }

        JSONObject response = new JSONObject();
        JSONArray tradesData = new JSONArray();
        if (askOrderId != 0 && bidOrderId != 0) {
            Trade trade = Trade.getTrade(askOrderId, bidOrderId);
            if (trade != null) {
                tradesData.add(JSONData.trade(trade, includeAssetInfo));
            }
        } else {
            H2Iterator<Trade> trades = null;
            try {
                if (askOrderId != 0) {
                    trades = Trade.getAskOrderTrades(askOrderId, firstIndex, lastIndex);
                } else {
                    trades = Trade.getBidOrderTrades(bidOrderId, firstIndex, lastIndex);
                }
                while (trades.hasNext()) {
                    Trade trade = trades.next();
                    tradesData.add(JSONData.trade(trade, includeAssetInfo));
                }
            } finally {
                H2Utils.h2close(trades);
            }
        }
        response.put("trades", tradesData);

        return response;
    }

}
