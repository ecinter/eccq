package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Property;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCoinsByIssuer extends APIRequestHandler {

    static final GetCoinsByIssuer instance = new GetCoinsByIssuer();

    private GetCoinsByIssuer() {
        super(new APITag[]{APITag.AE, APITag.ACCOUNTS}, "account", "account", "account", "firstIndex", "lastIndex", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long[] accountIds = ParameterParser.getAccountIds(req, true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));

        JSONObject response = new JSONObject();
        JSONArray accountsJSONArray = new JSONArray();
        response.put("assets", accountsJSONArray);
        for (long accountId : accountIds) {
            JSONArray assetsJSONArray = new JSONArray();
            try (H2Iterator<Property> assets = Property.getPropertysIssuedBy(accountId, firstIndex, lastIndex)) {
                while (assets.hasNext()) {
                    assetsJSONArray.add(JSONData.asset(assets.next(), includeCounts));
                }
            }
            accountsJSONArray.add(assetsJSONArray);
        }
        return response;
    }

}
