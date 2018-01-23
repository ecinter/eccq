package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Shuffling;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

public final class ShufflingVerify extends CreateTransaction {

    static final ShufflingVerify instance = new ShufflingVerify();

    private ShufflingVerify() {
        super(new APITag[]{APITag.SHUFFLING, APITag.CREATE_TRANSACTION}, "shuffling", "shufflingStateHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Shuffling shuffling = ParameterParser.getShuffling(req);
        byte[] shufflingStateHash = ParameterParser.getBytes(req, "shufflingStateHash", true);
        if (!Arrays.equals(shufflingStateHash, shuffling.getStateHash())) {
            return JSONResponses.incorrect("shufflingStateHash", "Shuffling is in a different state now");
        }
        Mortgaged mortgaged = new Mortgaged.ShufflingVerification(shuffling.getId(), shufflingStateHash);

        Account account = ParameterParser.getSenderAccount(req);
        return createTransaction(req, account, mortgaged);
    }
}
