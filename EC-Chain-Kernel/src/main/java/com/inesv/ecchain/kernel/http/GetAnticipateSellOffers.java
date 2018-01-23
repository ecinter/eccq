package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Coinage;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GetAnticipateSellOffers extends APIRequestHandler {

    static final GetAnticipateSellOffers instance = new GetAnticipateSellOffers();
    private final Comparator<Transaction> rateComparator = (o1, o2) -> {
        Mortgaged.MonetarySystemPublishExchangeOffer a1 = (Mortgaged.MonetarySystemPublishExchangeOffer) o1.getAttachment();
        Mortgaged.MonetarySystemPublishExchangeOffer a2 = (Mortgaged.MonetarySystemPublishExchangeOffer) o2.getAttachment();
        return Long.compare(a1.getSellRateNQT(), a2.getSellRateNQT());
    };

    private GetAnticipateSellOffers() {
        super(new APITag[]{APITag.MS}, "currency", "account", "sortByRate");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        long accountId = ParameterParser.getAccountId(req, "account", false);
        boolean sortByRate = "true".equalsIgnoreCase(req.getParameter("sortByRate"));

        Filter<Transaction> filter = transaction -> {
            if (transaction.getTransactionType() != Coinage.EC_PUBLISH_EXCHANGE_OFFER) {
                return false;
            }
            if (accountId != 0 && transaction.getSenderId() != accountId) {
                return false;
            }
            Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
            return currencyId == 0 || attachment.getCurrencyId() == currencyId;
        };

        List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);
        if (sortByRate) {
            Collections.sort(transactions, rateComparator);
        }

        JSONObject response = new JSONObject();
        JSONArray offerData = new JSONArray();
        transactions.forEach(transaction -> offerData.add(JSONData.expectedSellOffer(transaction)));
        response.put("offers", offerData);
        return response;
    }

}
