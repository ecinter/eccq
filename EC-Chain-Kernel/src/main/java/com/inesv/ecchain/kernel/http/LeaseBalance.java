package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class LeaseBalance extends CreateTransaction {

    static final LeaseBalance instance = new LeaseBalance();

    private LeaseBalance() {
        super(new APITag[]{APITag.FORGING, APITag.ACCOUNT_CONTROL, APITag.CREATE_TRANSACTION}, "period", "recipient");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        int period = ParameterParser.getInt(req, "period", Constants.EC_LEASING_DELAY, 65535, true);
        Account account = ParameterParser.getSenderAccount(req);
        long recipient = ParameterParser.getAccountId(req, "recipient", true);
        Account recipientAccount = Account.getAccount(recipient);
        if (recipientAccount == null || Account.getPublicKey(recipientAccount.getId()) == null) {
            JSONObject response = new JSONObject();
            response.put("errorCode", 8);
            response.put("errorDescription", "recipient account does not have public key");
            return response;
        }
        Mortgaged mortgaged = new Mortgaged.AccountControlEffectiveBalanceLeasing(period);
        return createTransaction(req, account, recipient, 0, mortgaged);

    }

}
