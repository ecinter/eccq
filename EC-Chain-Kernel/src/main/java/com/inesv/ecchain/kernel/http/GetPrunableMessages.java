package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.PrunableMessage;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetPrunableMessages extends APIRequestHandler {

    static final GetPrunableMessages instance = new GetPrunableMessages();

    private GetPrunableMessages() {
        super(new APITag[]{APITag.MESSAGES}, "account", "otherAccount", "secretPhrase", "firstIndex", "lastIndex", "timestamp");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long accountId = ParameterParser.getAccountId(req, true);
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        final int timestamp = ParameterParser.getTimestamp(req);
        long otherAccountId = ParameterParser.getAccountId(req, "otherAccount", false);

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        response.put("prunableMessages", jsonArray);

        try (H2Iterator<PrunableMessage> messages = otherAccountId == 0 ? PrunableMessage.getPrunableMessages(accountId, firstIndex, lastIndex)
                : PrunableMessage.getPrunableMessages(accountId, otherAccountId, firstIndex, lastIndex)) {
            while (messages.hasNext()) {
                PrunableMessage prunableMessage = messages.next();
                if (prunableMessage.getBlockTimestamp() < timestamp) {
                    break;
                }
                jsonArray.add(JSONData.prunableMessage(prunableMessage, secretPhrase, null));
            }
        }
        return response;
    }

}
