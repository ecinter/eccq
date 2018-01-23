package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.PhasingPoll;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetPhasingVote extends APIRequestHandler {

    static final GetPhasingVote instance = new GetPhasingVote();

    private GetPhasingVote() {
        super(new APITag[]{APITag.PHASING}, "transaction", "countVotes");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        boolean countVotes = "true".equalsIgnoreCase(req.getParameter("countVotes"));
        PhasingPoll phasingPoll = PhasingPoll.getPoll(transactionId);
        if (phasingPoll != null) {
            return JSONData.phasingPoll(phasingPoll, countVotes);
        }
        PhasingPoll.PhasingPollResult pollResult = PhasingPoll.getResult(transactionId);
        if (pollResult != null) {
            return JSONData.phasingPollResult(pollResult);
        }
        return JSONResponses.UNKNOWN_TRANSACTION;
    }
}