package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcPurchaseCount extends APIRequestHandler {

    static final GetEcPurchaseCount instance = new GetEcPurchaseCount();

    private GetEcPurchaseCount() {
        super(new APITag[]{APITag.DGS}, "seller", "buyer", "withPublicFeedbacksOnly", "completed");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long sellerId = ParameterParser.getAccountId(req, "seller", false);
        long buyerId = ParameterParser.getAccountId(req, "buyer", false);
        final boolean completed = "true".equalsIgnoreCase(req.getParameter("completed"));
        final boolean withPublicFeedbacksOnly = "true".equalsIgnoreCase(req.getParameter("withPublicFeedbacksOnly"));

        JSONObject response = new JSONObject();
        int count;
        if (sellerId != 0 && buyerId == 0) {
            count = ElectronicProductStore.Purchase.getSellerPurchaseCount(sellerId, withPublicFeedbacksOnly, completed);
        } else if (sellerId == 0 && buyerId != 0) {
            count = ElectronicProductStore.Purchase.getBuyerPurchaseCount(buyerId, withPublicFeedbacksOnly, completed);
        } else if (sellerId == 0 && buyerId == 0) {
            count = ElectronicProductStore.Purchase.getCount(withPublicFeedbacksOnly, completed);
        } else {
            count = ElectronicProductStore.Purchase.getSellerBuyerPurchaseCount(sellerId, buyerId, withPublicFeedbacksOnly, completed);
        }
        response.put("numberOfPurchases", count);
        return response;
    }

}
