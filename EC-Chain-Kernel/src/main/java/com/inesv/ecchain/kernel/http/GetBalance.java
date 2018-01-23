package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetBalance extends APIRequestHandler {

    static final GetBalance instance = new GetBalance();

    private GetBalance() {
        super(new APITag[]{APITag.ACCOUNTS}, "account", "includeEffectiveBalance", "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        boolean includeEffectiveBalance = "true".equalsIgnoreCase(req.getParameter("includeEffectiveBalance"));
        long accountId = ParameterParser.getAccountId(req, true);
        int height = ParameterParser.getHeight(req);
        if (height < 0) {
            height = EcBlockchainImpl.getInstance().getHeight();
        }
        Account account = Account.getAccount(accountId, height);
        return JSONData.accountBalance(account, includeEffectiveBalance, height);
    }

}
