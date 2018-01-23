package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.AccountRestrictions;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAllPhasingOnlyControls extends APIRequestHandler {

    static final GetAllPhasingOnlyControls instance = new GetAllPhasingOnlyControls();

    private GetAllPhasingOnlyControls() {
        super(new APITag[]{APITag.ACCOUNT_CONTROL}, "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        try (H2Iterator<AccountRestrictions.PhasingOnly> iterator = AccountRestrictions.PhasingOnly.getAll(firstIndex, lastIndex)) {
            for (AccountRestrictions.PhasingOnly phasingOnly : iterator) {
                jsonArray.add(JSONData.phasingOnly(phasingOnly));
            }
        }
        response.put("phasingOnlyControls", jsonArray);
        return response;
    }

}
