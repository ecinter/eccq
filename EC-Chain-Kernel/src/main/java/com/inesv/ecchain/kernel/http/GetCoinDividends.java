package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.PropertyDividend;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCoinDividends extends APIRequestHandler {

    static final GetCoinDividends instance = new GetCoinDividends();

    private GetCoinDividends() {
        super(new APITag[]{APITag.AE}, "asset", "firstIndex", "lastIndex", "timestamp");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long assetId = ParameterParser.getUnsignedLong(req, "asset", false);
        int timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray dividendsData = new JSONArray();
        try (H2Iterator<PropertyDividend> dividends = PropertyDividend.getAssetDividends(assetId, firstIndex, lastIndex)) {
            while (dividends.hasNext()) {
                PropertyDividend propertyDividend = dividends.next();
                if (propertyDividend.getTimestamp() < timestamp) {
                    break;
                }
                dividendsData.add(JSONData.assetDividend(propertyDividend));
            }
        }
        response.put("dividends", dividendsData);
        return response;
    }

}
