package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.PhasingParams;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class SetPhasingOnlyControl extends CreateTransaction {

    static final SetPhasingOnlyControl instance = new SetPhasingOnlyControl();

    private SetPhasingOnlyControl() {
        super(new APITag[]{APITag.ACCOUNT_CONTROL, APITag.CREATE_TRANSACTION}, "controlVotingModel", "controlQuorum", "controlMinBalance",
                "controlMinBalanceModel", "controlHolding", "controlWhitelisted", "controlWhitelisted", "controlWhitelisted",
                "controlMaxFees", "controlMinDuration", "controlMaxDuration");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws EcException {
        Account account = ParameterParser.getSenderAccount(request);
        PhasingParams phasingParams = parsePhasingParams(request, "control");
        long maxFees = ParameterParser.getLong(request, "controlMaxFees", 0, Constants.EC_MAX_BALANCE_NQT, false);
        short minDuration = (short) ParameterParser.getInt(request, "controlMinDuration", 0, Constants.EC_MAX_PHASING_DURATION - 1, false);
        short maxDuration = (short) ParameterParser.getInt(request, "controlMaxDuration", 0, Constants.EC_MAX_PHASING_DURATION - 1, false);
        return createTransaction(request, account, new Mortgaged.SetPhasingOnly(phasingParams, maxFees, minDuration, maxDuration));
    }

}
