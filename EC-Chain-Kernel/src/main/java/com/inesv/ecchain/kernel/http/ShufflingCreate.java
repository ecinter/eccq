package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.ControlType;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.HoldingType;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class ShufflingCreate extends CreateTransaction {

    static final ShufflingCreate instance = new ShufflingCreate();

    private ShufflingCreate() {
        super(new APITag[]{APITag.SHUFFLING, APITag.CREATE_TRANSACTION},
                "holding", "holdingType", "amount", "participantCount", "registrationPeriod");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        HoldingType holdingType = ParameterParser.getHoldingType(req);
        long holdingId = ParameterParser.getHoldingId(req, holdingType);
        long amount = ParameterParser.getLong(req, "amount", 0L, Long.MAX_VALUE, true);
        if (holdingType == HoldingType.EC && amount < Constants.EC_SHUFFLING_DEPOSIT_NQT) {
            return JSONResponses.incorrect("amount", "Minimum shuffling amount is " + Constants.EC_SHUFFLING_DEPOSIT_NQT / Constants.ONE_EC + " EC");
        }
        byte participantCount = ParameterParser.getByte(req, "participantCount", Constants.EC_MIN_NUMBER_OF_SHUFFLING_PARTICIPANTS,
                Constants.EC_MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS, true);
        short registrationPeriod = (short) ParameterParser.getInt(req, "registrationPeriod", 0, Constants.EC_MAX_SHUFFLING_REGISTRATION_PERIOD, true);
        Mortgaged mortgaged = new Mortgaged.ShufflingCreation(holdingId, holdingType, amount, participantCount, registrationPeriod);
        Account account = ParameterParser.getSenderAccount(req);
        if (account.getControls().contains(ControlType.PHASING_ONLY)) {
            return JSONResponses.error("Accounts under phasing only control cannot start a shuffling");
        }
        try {
            return createTransaction(req, account, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return JSONResponses.notEnoughHolding(holdingType);
        }
    }

}
