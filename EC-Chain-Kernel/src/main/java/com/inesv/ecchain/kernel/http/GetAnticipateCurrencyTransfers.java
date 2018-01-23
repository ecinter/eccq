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

public final class GetAnticipateCurrencyTransfers extends APIRequestHandler {

    static final GetAnticipateCurrencyTransfers instance = new GetAnticipateCurrencyTransfers();

    private GetAnticipateCurrencyTransfers() {
        super(new APITag[]{APITag.MS}, "currency", "account", "includeCurrencyInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        long accountId = ParameterParser.getAccountId(req, "account", false);
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));

        Filter<Transaction> filter = transaction -> {
            if (transaction.getTransactionType() != Coinage.EC_CURRENCY_TRANSFER) {
                return false;
            }
            if (accountId != 0 && transaction.getSenderId() != accountId && transaction.getRecipientId() != accountId) {
                return false;
            }
            Mortgaged.MonetarySystemCurrencyTransfer attachment = (Mortgaged.MonetarySystemCurrencyTransfer) transaction.getAttachment();
            return currencyId == 0 || attachment.getCurrencyId() == currencyId;
        };

        List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);

        JSONObject response = new JSONObject();
        JSONArray transfersData = new JSONArray();
        transactions.forEach(transaction -> transfersData.add(JSONData.expectedCurrencyTransfer(transaction, includeCurrencyInfo)));
        response.put("transfers", transfersData);

        return response;
    }

}
