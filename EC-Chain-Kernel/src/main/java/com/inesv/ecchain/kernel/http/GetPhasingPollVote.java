package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.PhasingVote;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetPhasingPollVote extends APIRequestHandler {
    static final GetPhasingPollVote instance = new GetPhasingPollVote();

    private GetPhasingPollVote() {
        super(new APITag[]{APITag.PHASING}, "transaction", "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        long accountId = ParameterParser.getAccountId(req, true);

        PhasingVote phasingVote = PhasingVote.getVote(transactionId, accountId);
        if (phasingVote != null) {
            return JSONData.phasingPollVote(phasingVote);
        }
        return JSON.EMPTY_JSON;
    }
}
