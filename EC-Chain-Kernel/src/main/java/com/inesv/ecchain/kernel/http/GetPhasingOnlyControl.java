package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.AccountRestrictions;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class GetPhasingOnlyControl extends APIRequestHandler {

    static final GetPhasingOnlyControl instance = new GetPhasingOnlyControl();

    private GetPhasingOnlyControl() {
        super(new APITag[]{APITag.ACCOUNT_CONTROL}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long accountId = ParameterParser.getAccountId(req, true);
        AccountRestrictions.PhasingOnly phasingOnly = AccountRestrictions.PhasingOnly.get(accountId);
        return phasingOnly == null ? JSON.EMPTY_JSON : JSONData.phasingOnly(phasingOnly);
    }

}
