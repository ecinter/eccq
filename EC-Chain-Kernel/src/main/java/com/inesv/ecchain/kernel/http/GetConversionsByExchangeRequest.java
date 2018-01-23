package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Conversion;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.MISSING_TRANSACTION;

public final class GetConversionsByExchangeRequest extends APIRequestHandler {

    static final GetConversionsByExchangeRequest instance = new GetConversionsByExchangeRequest();

    private GetConversionsByExchangeRequest() {
        super(new APITag[]{APITag.MS}, "transaction", "includeCurrencyInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        String transactionIdString = Convert.emptyToNull(req.getParameter("transaction"));
        if (transactionIdString == null) {
            return MISSING_TRANSACTION;
        }
        long transactionId = Convert.parseUnsignedLong(transactionIdString);
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));
        JSONObject response = new JSONObject();
        JSONArray exchangesData = new JSONArray();
        try (H2Iterator<Conversion> exchanges = Conversion.getConvert(transactionId)) {
            while (exchanges.hasNext()) {
                exchangesData.add(JSONData.exchange(exchanges.next(), includeCurrencyInfo));
            }
        }
        response.put("exchanges", exchangesData);
        return response;
    }

}
