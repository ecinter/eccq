package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Order;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllOpenOrders extends APIRequestHandler {

    static final GetAllOpenOrders instance = new GetAllOpenOrders();

    private GetAllOpenOrders() {
        super(new APITag[]{APITag.AE}, "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = new JSONObject();
        JSONArray ordersData = new JSONArray();

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        try (H2Iterator<Order.Ask> askOrders = Order.Ask.getAll(firstIndex, lastIndex)) {
            while (askOrders.hasNext()) {
                ordersData.add(JSONData.askOrder(askOrders.next()));
            }
        }

        response.put("openOrders", ordersData);
        return response;
    }

}
