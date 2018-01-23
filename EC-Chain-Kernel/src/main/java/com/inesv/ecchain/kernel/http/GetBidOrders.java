package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

public final class GetBidOrders extends APIRequestHandler {

    static final GetBidOrders instance = new GetBidOrders();

    private GetBidOrders() {
        super(new APITag[]{APITag.AE}, "asset", "firstIndex", "lastIndex", "showExpectedCancellations");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long assetId = ParameterParser.getUnsignedLong(req, "asset", true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean showExpectedCancellations = "true".equalsIgnoreCase(req.getParameter("showExpectedCancellations"));

        long[] cancellations = null;
        if (showExpectedCancellations) {
            Filter<Transaction> filter = transaction -> transaction.getTransactionType() == ColoredCoins.BID_ORDER_CANCELLATION;
            List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);
            cancellations = new long[transactions.size()];
            for (int i = 0; i < transactions.size(); i++) {
                Mortgaged.ColoredCoinsOrderCancellation attachment = (Mortgaged.ColoredCoinsOrderCancellation) transactions.get(i).getAttachment();
                cancellations[i] = attachment.getOrderId();
            }
            Arrays.sort(cancellations);
        }

        JSONArray orders = new JSONArray();
        try (H2Iterator<Order.Bid> bidOrders = Order.Bid.getSortedOrders(assetId, firstIndex, lastIndex)) {
            while (bidOrders.hasNext()) {
                Order.Bid order = bidOrders.next();
                JSONObject orderJSON = JSONData.bidOrder(order);
                if (showExpectedCancellations && Arrays.binarySearch(cancellations, order.getId()) >= 0) {
                    orderJSON.put("expectedCancellation", Boolean.TRUE);
                }
                orders.add(orderJSON);
            }
        }
        JSONObject response = new JSONObject();
        response.put("bidOrders", orders);
        return response;
    }

}
