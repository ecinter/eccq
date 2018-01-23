package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCurrencyAccountCount extends APIRequestHandler {

    static final GetCurrencyAccountCount instance = new GetCurrencyAccountCount();

    private GetCurrencyAccountCount() {
        super(new APITag[]{APITag.MS}, "currency", "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", true);
        int height = ParameterParser.getHeight(req);

        JSONObject response = new JSONObject();
        response.put("numberOfAccounts", Account.getCoinAccountCount(currencyId, height));
        return response;

    }

}
