package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Coinage;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetAnticipateExchangeRequests extends APIRequestHandler {

    static final GetAnticipateExchangeRequests instance = new GetAnticipateExchangeRequests();

    private GetAnticipateExchangeRequests() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.MS}, "account", "currency", "includeCurrencyInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, "account", false);
        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));

        Filter<Transaction> filter = transaction -> {
            if (transaction.getTransactionType() != Coinage.EC_EXCHANGE_BUY && transaction.getTransactionType() != Coinage.EC_EXCHANGE_SELL) {
                return false;
            }
            if (accountId != 0 && transaction.getSenderId() != accountId) {
                return false;
            }
            Mortgaged.MonetarySystemExchange attachment = (Mortgaged.MonetarySystemExchange) transaction.getAttachment();
            return currencyId == 0 || attachment.getCurrencyId() == currencyId;
        };

        List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);

        JSONArray exchangeRequests = new JSONArray();
        transactions.forEach(transaction -> exchangeRequests.add(JSONData.expectedExchangeRequest(transaction, includeCurrencyInfo)));
        JSONObject response = new JSONObject();
        response.put("exchangeRequests", exchangeRequests);
        return response;

    }

}
