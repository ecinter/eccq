package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Conversion;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetLastConversions extends APIRequestHandler {

    static final GetLastConversions instance = new GetLastConversions();

    private GetLastConversions() {
        super(new APITag[]{APITag.MS}, "currencies", "currencies", "currencies"); // limit to 3 for testing
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long[] currencyIds = ParameterParser.getUnsignedLongs(req, "currencies");
        JSONArray exchangesJSON = new JSONArray();
        List<Conversion> conversions = Conversion.getLastConvert(currencyIds);
        conversions.forEach(exchange -> exchangesJSON.add(JSONData.exchange(exchange, false)));
        JSONObject response = new JSONObject();
        response.put("conversions", exchangesJSON);
        return response;
    }

}
