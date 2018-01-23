package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.AccountName;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountNameCount extends APIRequestHandler {

    static final GetAccountNameCount instance = new GetAccountNameCount();

    private GetAccountNameCount() {
        super(new APITag[]{APITag.ALIASES}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        final long accountId = ParameterParser.getAccountId(req, true);
        JSONObject response = new JSONObject();
        response.put("numberOfAliases", AccountName.getAccountPropertysCount(accountId));
        return response;
    }

}
