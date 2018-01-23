package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ConversionRequest;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountConversionRequests extends APIRequestHandler {

    static final GetAccountConversionRequests instance = new GetAccountConversionRequests();

    private GetAccountConversionRequests() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.MS}, "account", "currency", "includeCurrencyInfo", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        long currencyId = ParameterParser.getUnsignedLong(req, "currency", true);
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray jsonArray = new JSONArray();
        try (H2Iterator<ConversionRequest> exchangeRequests = ConversionRequest.getAccountCurrencyExchangeRequests(accountId, currencyId,
                firstIndex, lastIndex)) {
            while (exchangeRequests.hasNext()) {
                jsonArray.add(JSONData.exchangeRequest(exchangeRequests.next(), includeCurrencyInfo));
            }
        }
        JSONObject response = new JSONObject();
        response.put("exchangeRequests", jsonArray);
        return response;
    }

}
