package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GetAnticipateBidOrders extends APIRequestHandler {

    static final GetAnticipateBidOrders instance = new GetAnticipateBidOrders();
    private final Comparator<Transaction> priceComparator = (o1, o2) -> {
        Mortgaged.ColoredCoinsOrderPlacement a1 = (Mortgaged.ColoredCoinsOrderPlacement) o1.getAttachment();
        Mortgaged.ColoredCoinsOrderPlacement a2 = (Mortgaged.ColoredCoinsOrderPlacement) o2.getAttachment();
        return Long.compare(a2.getPriceNQT(), a1.getPriceNQT());
    };

    private GetAnticipateBidOrders() {
        super(new APITag[]{APITag.AE}, "asset", "sortByPrice");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long assetId = ParameterParser.getUnsignedLong(req, "asset", false);
        boolean sortByPrice = "true".equalsIgnoreCase(req.getParameter("sortByPrice"));
        Filter<Transaction> filter = transaction -> {
            if (transaction.getTransactionType() != ColoredCoins.BID_ORDER_PLACEMENT) {
                return false;
            }
            Mortgaged.ColoredCoinsOrderPlacement attachment = (Mortgaged.ColoredCoinsOrderPlacement) transaction.getAttachment();
            return assetId == 0 || attachment.getAssetId() == assetId;
        };

        List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);
        if (sortByPrice) {
            Collections.sort(transactions, priceComparator);
        }
        JSONArray orders = new JSONArray();
        transactions.forEach(transaction -> orders.add(JSONData.expectedBidOrder(transaction)));
        JSONObject response = new JSONObject();
        response.put("bidOrders", orders);
        return response;

    }

}
