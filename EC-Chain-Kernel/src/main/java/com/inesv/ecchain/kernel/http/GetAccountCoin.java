package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountCoin extends APIRequestHandler {

    static final GetAccountCoin instance = new GetAccountCoin();

    private GetAccountCoin() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.MS}, "account", "currency", "height", "includeCurrencyInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        int height = ParameterParser.getHeight(req);
        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));

        if (currencyId == 0) {
            JSONObject response = new JSONObject();
            try (H2Iterator<Account.AccountCoin> accountCurrencies = Account.getAccountCoins(accountId, height, 0, -1)) {
                JSONArray currencyJSON = new JSONArray();
                while (accountCurrencies.hasNext()) {
                    currencyJSON.add(JSONData.accountCurrency(accountCurrencies.next(), false, includeCurrencyInfo));
                }
                response.put("accountCurrencies", currencyJSON);
                return response;
            }
        } else {
            Account.AccountCoin accountCoin = Account.getAccountCoin(accountId, currencyId, height);
            if (accountCoin != null) {
                return JSONData.accountCurrency(accountCoin, false, includeCurrencyInfo);
            }
            return JSON.EMPTY_JSON;
        }
    }

}
