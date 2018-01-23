package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.BadgeData;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SearchTaggedData extends APIRequestHandler {

    static final SearchTaggedData instance = new SearchTaggedData();

    private SearchTaggedData() {
        super(new APITag[]{APITag.DATA, APITag.SEARCH}, "query", "tag", "channel", "account", "firstIndex", "lastIndex", "includeData");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long accountId = ParameterParser.getAccountId(req, "account", false);
        String query = ParameterParser.getSearchQuery(req);
        String channel = Convert.emptyToNull(req.getParameter("channel"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeData = "true".equalsIgnoreCase(req.getParameter("includeData"));

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        response.put("data", jsonArray);
        try (H2Iterator<BadgeData> data = BadgeData.searchData(query, channel, accountId, firstIndex, lastIndex)) {
            while (data.hasNext()) {
                jsonArray.add(JSONData.taggedData(data.next(), includeData));
            }
        }

        return response;
    }

}
