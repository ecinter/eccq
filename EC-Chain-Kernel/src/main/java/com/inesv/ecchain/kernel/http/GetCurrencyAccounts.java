package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCurrencyAccounts extends APIRequestHandler {

    static final GetCurrencyAccounts instance = new GetCurrencyAccounts();

    private GetCurrencyAccounts() {
        super(new APITag[]{APITag.MS}, "currency", "height", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        int height = ParameterParser.getHeight(req);

        JSONArray accountCurrencies = new JSONArray();
        try (H2Iterator<Account.AccountCoin> iterator = Account.getCoinAccounts(currencyId, height, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                Account.AccountCoin accountCoin = iterator.next();
                accountCurrencies.add(JSONData.accountCurrency(accountCoin, true, false));
            }
        }

        JSONObject response = new JSONObject();
        response.put("accountCurrencies", accountCurrencies);
        return response;

    }

}
