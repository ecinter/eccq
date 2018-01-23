package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.CoinFounder;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCurrencyFounders extends APIRequestHandler {

    static final GetCurrencyFounders instance = new GetCurrencyFounders();

    private GetCurrencyFounders() {
        super(new APITag[]{APITag.MS}, "currency", "account", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        long accountId = ParameterParser.getAccountId(req, false);
        if (currencyId == 0 && accountId == 0) {
            return JSONResponses.MISSING_CURRENCY_ACCOUNT;
        }
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray foundersJSONArray = new JSONArray();
        response.put("founders", foundersJSONArray);

        if (currencyId != 0 && accountId != 0) {
            CoinFounder coinFounder = CoinFounder.getFounder(currencyId, accountId);
            if (coinFounder != null) {
                foundersJSONArray.add(JSONData.currencyFounder(coinFounder));
            }
            return response;
        }

        H2Iterator<CoinFounder> founders = null;
        try {
            if (accountId == 0) {
                founders = CoinFounder.getCurrencyFounders(currencyId, firstIndex, lastIndex);
            } else if (currencyId == 0) {
                founders = CoinFounder.getFounderCurrencies(accountId, firstIndex, lastIndex);
            }
            for (CoinFounder founder : founders) {
                foundersJSONArray.add(JSONData.currencyFounder(founder));
            }
        } finally {
            H2Utils.h2close(founders);
        }
        return response;
    }
}
