package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetGuaranteedBalance extends APIRequestHandler {

    static final GetGuaranteedBalance instance = new GetGuaranteedBalance();

    private GetGuaranteedBalance() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.FORGING}, "account", "numberOfConfirmations");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Account account = ParameterParser.getAccount(req);
        int numberOfConfirmations = ParameterParser.getNumberOfConfirmations(req);

        JSONObject response = new JSONObject();
        if (account == null) {
            response.put("guaranteedBalanceNQT", "0");
        } else {
            response.put("guaranteedBalanceNQT", String.valueOf(account.getGuaranteedBalanceNQT(numberOfConfirmations, EcBlockchainImpl.getInstance().getHeight())));
        }

        return response;
    }

}
