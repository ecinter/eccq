package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Property;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllAccountNamets extends APIRequestHandler {

    static final GetAllAccountNamets instance = new GetAllAccountNamets();

    private GetAllAccountNamets() {
        super(new APITag[]{APITag.AE}, "firstIndex", "lastIndex", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));

        JSONObject response = new JSONObject();
        JSONArray assetsJSONArray = new JSONArray();
        response.put("assets", assetsJSONArray);
        try (H2Iterator<Property> assets = Property.getAllAssets(firstIndex, lastIndex)) {
            while (assets.hasNext()) {
                assetsJSONArray.add(JSONData.asset(assets.next(), includeCounts));
            }
        }
        return response;
    }

}
