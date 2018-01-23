package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Property;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_ASSET;


public final class GetCoins extends APIRequestHandler {

    static final GetCoins instance = new GetCoins();

    private GetCoins() {
        super(new APITag[]{APITag.AE}, "assets", "assets", "assets", "includeCounts"); // limit to 3 for testing
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long[] assetIds = ParameterParser.getUnsignedLongs(req, "assets");
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));
        JSONObject response = new JSONObject();
        JSONArray assetsJSONArray = new JSONArray();
        response.put("assets", assetsJSONArray);
        for (long assetId : assetIds) {
            Property property = Property.getAsset(assetId);
            if (property == null) {
                return UNKNOWN_ASSET;
            }
            assetsJSONArray.add(JSONData.asset(property, includeCounts));
        }
        return response;
    }

}
