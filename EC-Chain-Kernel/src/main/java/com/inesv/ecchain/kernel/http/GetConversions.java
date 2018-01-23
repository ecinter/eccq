package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Conversion;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetConversions extends APIRequestHandler {

    static final GetConversions instance = new GetConversions();

    private GetConversions() {
        super(new APITag[]{APITag.MS}, "currency", "account", "firstIndex", "lastIndex", "timestamp", "includeCurrencyInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        int timestamp = ParameterParser.getTimestamp(req);
        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        long accountId = ParameterParser.getAccountId(req, false);
        if (currencyId == 0 && accountId == 0) {
            return JSONResponses.MISSING_CURRENCY_ACCOUNT;
        }
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray exchangesData = new JSONArray();
        H2Iterator<Conversion> exchanges = null;
        try {
            if (accountId == 0) {
                exchanges = Conversion.getCoinExchanges(currencyId, firstIndex, lastIndex);
            } else if (currencyId == 0) {
                exchanges = Conversion.getAccountConvert(accountId, firstIndex, lastIndex);
            } else {
                exchanges = Conversion.getAccountCurrencyConvert(accountId, currencyId, firstIndex, lastIndex);
            }
            while (exchanges.hasNext()) {
                Conversion conversion = exchanges.next();
                if (conversion.getTimestamp() < timestamp) {
                    break;
                }
                exchangesData.add(JSONData.exchange(conversion, includeCurrencyInfo));
            }
        } finally {
            H2Utils.h2close(exchanges);
        }
        response.put("exchanges", exchangesData);

        return response;
    }

    @Override
    protected boolean startDbTransaction() {
        return true;
    }

}
