package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Shuffling;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.incorrect;

public final class GetHoldingShufflings extends APIRequestHandler {

    static final GetHoldingShufflings instance = new GetHoldingShufflings();

    private GetHoldingShufflings() {
        super(new APITag[]{APITag.SHUFFLING}, "holding", "stage", "includeFinished", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        long holdingId = 0;
        String holdingValue = Convert.emptyToNull(req.getParameter("holding"));
        if (holdingValue != null) {
            try {
                holdingId = Convert.parseUnsignedLong(holdingValue);
            } catch (RuntimeException e) {
                return incorrect("holding");
            }
        }
        String stageValue = Convert.emptyToNull(req.getParameter("stage"));
        Shuffling.Stage stage = null;
        if (stageValue != null) {
            try {
                stage = Shuffling.Stage.get(Byte.parseByte(stageValue));
            } catch (RuntimeException e) {
                return incorrect("stage");
            }
        }
        boolean includeFinished = "true".equalsIgnoreCase(req.getParameter("includeFinished"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        response.put("shufflings", jsonArray);
        try (H2Iterator<Shuffling> shufflings = Shuffling.getHoldingShufflings(holdingId, stage, includeFinished, firstIndex, lastIndex)) {
            for (Shuffling shuffling : shufflings) {
                jsonArray.add(JSONData.shuffling(shuffling, false));
            }
        }
        return response;
    }

}
