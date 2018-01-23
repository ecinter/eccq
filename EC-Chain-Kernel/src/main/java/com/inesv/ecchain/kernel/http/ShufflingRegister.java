package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.ControlType;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Shuffling;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class ShufflingRegister extends CreateTransaction {

    static final ShufflingRegister instance = new ShufflingRegister();

    private ShufflingRegister() {
        super(new APITag[]{APITag.SHUFFLING, APITag.CREATE_TRANSACTION}, "shufflingFullHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        byte[] shufflingFullHash = ParameterParser.getBytes(req, "shufflingFullHash", true);

        Mortgaged mortgaged = new Mortgaged.ShufflingRegistration(shufflingFullHash);

        Account account = ParameterParser.getSenderAccount(req);
        if (account.getControls().contains(ControlType.PHASING_ONLY)) {
            return JSONResponses.error("Accounts under phasing only control cannot join a shuffling");
        }
        try {
            return createTransaction(req, account, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            Shuffling shuffling = Shuffling.getShuffling(shufflingFullHash);
            if (shuffling == null) {
                return JSONResponses.NOT_ENOUGH_FUNDS;
            }
            return JSONResponses.notEnoughHolding(shuffling.getHoldingType());
        }
    }

}
