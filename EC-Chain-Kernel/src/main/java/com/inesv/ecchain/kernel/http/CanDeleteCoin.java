package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class CanDeleteCoin extends APIRequestHandler {

    static final CanDeleteCoin instance = new CanDeleteCoin();

    private CanDeleteCoin() {
        super(new APITag[]{APITag.MS}, "account", "currency");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        long accountId = ParameterParser.getAccountId(req, true);
        JSONObject response = new JSONObject();
        response.put("canDelete", coin.canBeDeletedBy(accountId));
        return response;
    }

}
