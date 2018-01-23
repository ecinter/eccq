package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Shuffling;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class ShufflingCancel extends CreateTransaction {

    static final ShufflingCancel instance = new ShufflingCancel();

    private ShufflingCancel() {
        super(new APITag[]{APITag.SHUFFLING, APITag.CREATE_TRANSACTION}, "shuffling", "cancellingAccount", "shufflingStateHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Shuffling shuffling = ParameterParser.getShuffling(req);
        long cancellingAccountId = ParameterParser.getAccountId(req, "cancellingAccount", false);
        byte[] shufflingStateHash = ParameterParser.getBytes(req, "shufflingStateHash", true);
        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        Mortgaged.ShufflingCancellation attachment = shuffling.revealKeySeeds(secretPhrase, cancellingAccountId, shufflingStateHash);
        Account account = ParameterParser.getSenderAccount(req);
        return createTransaction(req, account, attachment);
    }
}
