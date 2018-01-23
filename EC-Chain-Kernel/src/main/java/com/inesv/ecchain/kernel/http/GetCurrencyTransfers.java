package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.CoinTransfer;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCurrencyTransfers extends APIRequestHandler {

    static final GetCurrencyTransfers instance = new GetCurrencyTransfers();

    private GetCurrencyTransfers() {
        super(new APITag[]{APITag.MS}, "currency", "account", "firstIndex", "lastIndex", "timestamp", "includeCurrencyInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        long accountId = ParameterParser.getAccountId(req, false);
        if (currencyId == 0 && accountId == 0) {
            return JSONResponses.MISSING_CURRENCY_ACCOUNT;
        }
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));
        int timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray transfersData = new JSONArray();
        H2Iterator<CoinTransfer> transfers = null;
        try {
            if (accountId == 0) {
                transfers = CoinTransfer.getCoinTransfers(currencyId, firstIndex, lastIndex);
            } else if (currencyId == 0) {
                transfers = CoinTransfer.getAccountCoinTransfers(accountId, firstIndex, lastIndex);
            } else {
                transfers = CoinTransfer.getAccountCoinTransfers(accountId, currencyId, firstIndex, lastIndex);
            }
            while (transfers.hasNext()) {
                CoinTransfer coinTransfer = transfers.next();
                if (coinTransfer.getTimestamp() < timestamp) {
                    break;
                }
                transfersData.add(JSONData.currencyTransfer(coinTransfer, includeCurrencyInfo));
            }
        } finally {
            H2Utils.h2close(transfers);
        }
        response.put("transfers", transfersData);

        return response;
    }

    @Override
    protected boolean startDbTransaction() {
        return true;
    }

}
