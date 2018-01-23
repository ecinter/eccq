package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetPollVotes extends APIRequestHandler {
    static final GetPollVotes instance = new GetPollVotes();

    private GetPollVotes() {
        super(new APITag[]{APITag.VS}, "poll", "firstIndex", "lastIndex", "includeWeights");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeWeights = "true".equalsIgnoreCase(req.getParameter("includeWeights"));
        Poll poll = ParameterParser.getPoll(req);
        int countHeight;
        JSONData.VoteWeighter weighter = null;
        if (includeWeights && (countHeight = Math.min(poll.getFinishHeight(), EcBlockchainImpl.getInstance().getHeight()))
                >= EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight()) {
            VoteWeighting voteWeighting = poll.getVoteWeighting();
            VoteWeighting.VotingModel votingModel = voteWeighting.getVotingModel();
            weighter = voterId -> votingModel.calcWeight(voteWeighting, voterId, countHeight);
        }
        JSONArray votesJson = new JSONArray();
        try (H2Iterator<Vote> votes = Vote.getVotes(poll.getId(), firstIndex, lastIndex)) {
            for (Vote vote : votes) {
                votesJson.add(JSONData.vote(vote, weighter));
            }
        }
        JSONObject response = new JSONObject();
        response.put("votes", votesJson);
        return response;
    }
}
