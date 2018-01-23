package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.BadgeData;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetDataTags extends APIRequestHandler {

    static final GetDataTags instance = new GetDataTags();

    private GetDataTags() {
        super(new APITag[]{APITag.DATA}, "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray tagsJSON = new JSONArray();
        response.put("tags", tagsJSON);

        try (H2Iterator<BadgeData.Tag> tags = BadgeData.Tag.getAllTags(firstIndex, lastIndex)) {
            while (tags.hasNext()) {
                tagsJSON.add(JSONData.dataTag(tags.next()));
            }
        }
        return response;
    }

}
