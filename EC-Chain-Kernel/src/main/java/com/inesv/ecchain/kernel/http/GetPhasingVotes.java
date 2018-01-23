package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.PhasingPoll;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetPhasingVotes extends APIRequestHandler {

    static final GetPhasingVotes instance = new GetPhasingVotes();

    private GetPhasingVotes() {
        super(new APITag[]{APITag.PHASING}, "transaction", "transaction", "transaction", "countVotes"); // limit to 3 for testing
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long[] transactionIds = ParameterParser.getUnsignedLongs(req, "transaction");
        boolean countVotes = "true".equalsIgnoreCase(req.getParameter("countVotes"));
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        response.put("polls", jsonArray);
        for (long transactionId : transactionIds) {
            PhasingPoll poll = PhasingPoll.getPoll(transactionId);
            if (poll != null) {
                jsonArray.add(JSONData.phasingPoll(poll, countVotes));
            } else {
                PhasingPoll.PhasingPollResult pollResult = PhasingPoll.getResult(transactionId);
                if (pollResult != null) {
                    jsonArray.add(JSONData.phasingPollResult(pollResult));
                }
            }
        }
        return response;
    }

}
