package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.AccountName;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountName extends APIRequestHandler {

    static final GetAccountName instance = new GetAccountName();

    private GetAccountName() {
        super(new APITag[]{APITag.ALIASES}, "alias", "aliasName");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        AccountName accountName = ParameterParser.getAlias(req);
        return JSONData.alias(accountName);
    }

}
