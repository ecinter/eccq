package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetPollVote extends APIRequestHandler {
    static final GetPollVote instance = new GetPollVote();

    private GetPollVote() {
        super(new APITag[]{APITag.VS}, "poll", "account", "includeWeights");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Poll poll = ParameterParser.getPoll(req);
        long accountId = ParameterParser.getAccountId(req, true);
        boolean includeWeights = "true".equalsIgnoreCase(req.getParameter("includeWeights"));
        Vote vote = Vote.getVote(poll.getId(), accountId);
        if (vote != null) {
            int countHeight;
            JSONData.VoteWeighter weighter = null;
            if (includeWeights && (countHeight = Math.min(poll.getFinishHeight(), EcBlockchainImpl.getInstance().getHeight()))
                    >= EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight()) {
                VoteWeighting voteWeighting = poll.getVoteWeighting();
                VoteWeighting.VotingModel votingModel = voteWeighting.getVotingModel();
                weighter = voterId -> votingModel.calcWeight(voteWeighting, voterId, countHeight);
            }
            return JSONData.vote(vote, weighter);
        }
        return JSON.EMPTY_JSON;
    }
}
