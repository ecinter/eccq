package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Poll;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_VOTE;
import static com.inesv.ecchain.kernel.http.JSONResponses.POLL_FINISHED;


public final class CastVote extends CreateTransaction {

    static final CastVote instance = new CastVote();

    private CastVote() {
        super(new APITag[]{APITag.VS, APITag.CREATE_TRANSACTION}, "poll", "vote00", "vote01", "vote02");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Poll poll = ParameterParser.getPoll(req);
        if (poll.isFinished()) {
            return POLL_FINISHED;
        }

        int numberOfOptions = poll.getOptions().length;
        byte[] vote = new byte[numberOfOptions];
        try {
            for (int i = 0; i < numberOfOptions; i++) {
                String voteValue = Convert.emptyToNull(req.getParameter("vote" + (i < 10 ? "0" + i : i)));
                if (voteValue != null) {
                    vote[i] = Byte.parseByte(voteValue);
                    if (vote[i] != Constants.EC_NO_VOTE_VALUE && (vote[i] < poll.getMinRangeValue() || vote[i] > poll.getMaxRangeValue())) {
                        return INCORRECT_VOTE;
                    }
                } else {
                    vote[i] = Constants.EC_NO_VOTE_VALUE;
                }
            }
        } catch (NumberFormatException e) {
            return INCORRECT_VOTE;
        }

        Account account = ParameterParser.getSenderAccount(req);
        Mortgaged mortgaged = new Mortgaged.MessagingVoteCasting(poll.getId(), vote);
        return createTransaction(req, account, mortgaged);
    }
}
