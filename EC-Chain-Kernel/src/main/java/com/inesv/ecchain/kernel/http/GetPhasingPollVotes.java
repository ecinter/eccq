package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.PhasingPoll;
import com.inesv.ecchain.kernel.core.PhasingVote;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetPhasingPollVotes extends APIRequestHandler {
    static final GetPhasingPollVotes instance = new GetPhasingPollVotes();

    private GetPhasingPollVotes() {
        super(new APITag[]{APITag.PHASING}, "transaction", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        PhasingPoll phasingPoll = PhasingPoll.getPoll(transactionId);
        if (phasingPoll != null) {
            JSONObject response = new JSONObject();
            JSONArray votesJSON = new JSONArray();
            try (H2Iterator<PhasingVote> votes = PhasingVote.getVotes(transactionId, firstIndex, lastIndex)) {
                for (PhasingVote vote : votes) {
                    votesJSON.add(JSONData.phasingPollVote(vote));
                }
            }
            response.put("votes", votesJSON);
            return response;
        }
        return JSONResponses.UNKNOWN_TRANSACTION;
    }
}
