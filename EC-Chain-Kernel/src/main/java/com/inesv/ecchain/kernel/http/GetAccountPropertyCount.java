package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountPropertyCount extends APIRequestHandler {

    static final GetAccountPropertyCount instance = new GetAccountPropertyCount();

    private GetAccountPropertyCount() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.AE}, "account", "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        int height = ParameterParser.getHeight(req);

        JSONObject response = new JSONObject();
        response.put("numberOfAssets", Account.getAccountPropertyCount(accountId, height));
        return response;
    }

}
