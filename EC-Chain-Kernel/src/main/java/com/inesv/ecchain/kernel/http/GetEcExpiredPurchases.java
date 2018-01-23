package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcExpiredPurchases extends APIRequestHandler {

    static final GetEcExpiredPurchases instance = new GetEcExpiredPurchases();

    private GetEcExpiredPurchases() {
        super(new APITag[]{APITag.DGS}, "seller", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long sellerId = ParameterParser.getAccountId(req, "seller", true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray purchasesJSON = new JSONArray();

        try (H2Iterator<ElectronicProductStore.Purchase> purchases = ElectronicProductStore.Purchase.getExpiredSellerPurchases(sellerId, firstIndex, lastIndex)) {
            while (purchases.hasNext()) {
                purchasesJSON.add(JSONData.purchase(purchases.next()));
            }
        }

        response.put("purchases", purchasesJSON);
        return response;
    }

}
