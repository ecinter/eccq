package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcPurchases extends APIRequestHandler {

    static final GetEcPurchases instance = new GetEcPurchases();

    private GetEcPurchases() {
        super(new APITag[]{APITag.DGS}, "seller", "buyer", "firstIndex", "lastIndex", "withPublicFeedbacksOnly", "completed");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long sellerId = ParameterParser.getAccountId(req, "seller", false);
        long buyerId = ParameterParser.getAccountId(req, "buyer", false);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        final boolean completed = "true".equalsIgnoreCase(req.getParameter("completed"));
        final boolean withPublicFeedbacksOnly = "true".equalsIgnoreCase(req.getParameter("withPublicFeedbacksOnly"));


        JSONObject response = new JSONObject();
        JSONArray purchasesJSON = new JSONArray();
        response.put("purchases", purchasesJSON);

        H2Iterator<ElectronicProductStore.Purchase> purchases;
        if (sellerId == 0 && buyerId == 0) {
            purchases = ElectronicProductStore.Purchase.getPurchases(withPublicFeedbacksOnly, completed, firstIndex, lastIndex);
        } else if (sellerId != 0 && buyerId == 0) {
            purchases = ElectronicProductStore.Purchase.getSellerPurchases(sellerId, withPublicFeedbacksOnly, completed, firstIndex, lastIndex);
        } else if (sellerId == 0) {
            purchases = ElectronicProductStore.Purchase.getBuyerPurchases(buyerId, withPublicFeedbacksOnly, completed, firstIndex, lastIndex);
        } else {
            purchases = ElectronicProductStore.Purchase.getSellerBuyerPurchases(sellerId, buyerId, withPublicFeedbacksOnly, completed, firstIndex, lastIndex);
        }
        try {
            while (purchases.hasNext()) {
                purchasesJSON.add(JSONData.purchase(purchases.next()));
            }
        } finally {
            H2Utils.h2close(purchases);
        }
        return response;
    }

}
