package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Shuffling;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllShufflings extends APIRequestHandler {

    static final GetAllShufflings instance = new GetAllShufflings();

    private GetAllShufflings() {
        super(new APITag[]{APITag.SHUFFLING}, "includeFinished", "includeHoldingInfo", "finishedOnly", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        boolean includeFinished = "true".equalsIgnoreCase(req.getParameter("includeFinished"));
        boolean finishedOnly = "true".equalsIgnoreCase(req.getParameter("finishedOnly"));
        boolean includeHoldingInfo = "true".equalsIgnoreCase(req.getParameter("includeHoldingInfo"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        response.put("shufflings", jsonArray);
        H2Iterator<Shuffling> shufflings = null;
        try {
            if (finishedOnly) {
                shufflings = Shuffling.getFinishedShufflings(firstIndex, lastIndex);
            } else if (includeFinished) {
                shufflings = Shuffling.getAllShuffling(firstIndex, lastIndex);
            } else {
                shufflings = Shuffling.getActiveShufflings(firstIndex, lastIndex);
            }
            for (Shuffling shuffling : shufflings) {
                jsonArray.add(JSONData.shuffling(shuffling, includeHoldingInfo));
            }
        } finally {
            H2Utils.h2close(shufflings);
        }
        return response;
    }

}
