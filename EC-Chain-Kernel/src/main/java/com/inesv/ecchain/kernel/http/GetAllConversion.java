package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Conversion;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllConversion extends APIRequestHandler {

    static final GetAllConversion instance = new GetAllConversion();

    private GetAllConversion() {
        super(new APITag[]{APITag.MS}, "timestamp", "firstIndex", "lastIndex", "includeCurrencyInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        final int timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));

        JSONObject response = new JSONObject();
        JSONArray exchanges = new JSONArray();
        try (H2Iterator<Conversion> exchangeIterator = Conversion.getAllExchanges(firstIndex, lastIndex)) {
            while (exchangeIterator.hasNext()) {
                Conversion conversion = exchangeIterator.next();
                if (conversion.getTimestamp() < timestamp) {
                    break;
                }
                exchanges.add(JSONData.exchange(conversion, includeCurrencyInfo));
            }
        }
        response.put("exchanges", exchanges);
        return response;
    }

}
