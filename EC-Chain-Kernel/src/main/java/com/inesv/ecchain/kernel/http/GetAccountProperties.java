package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountProperties extends APIRequestHandler {

    static final GetAccountProperties instance = new GetAccountProperties();

    private GetAccountProperties() {
        super(new APITag[]{APITag.ACCOUNTS}, "recipient", "property", "setter", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long recipientId = ParameterParser.getAccountId(req, "recipient", false);
        long setterId = ParameterParser.getAccountId(req, "setter", false);
        if (recipientId == 0 && setterId == 0) {
            return JSONResponses.missing("recipient", "setter");
        }
        String property = Convert.emptyToNull(req.getParameter("property"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray propertiesJSON = new JSONArray();
        response.put("properties", propertiesJSON);
        if (recipientId != 0) {
            JSONData.putAccount(response, "recipient", recipientId);
        }
        if (setterId != 0) {
            JSONData.putAccount(response, "setter", setterId);
        }
        try (H2Iterator<Account.AccountProperty> iterator = Account.getProperties(recipientId, setterId, property, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                propertiesJSON.add(JSONData.accountProperty(iterator.next(), recipientId == 0, setterId == 0));
            }
        }
        return response;

    }

}
