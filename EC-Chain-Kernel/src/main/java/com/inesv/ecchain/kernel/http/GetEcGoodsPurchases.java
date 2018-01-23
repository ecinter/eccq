package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcGoodsPurchases extends APIRequestHandler {

    static final GetEcGoodsPurchases instance = new GetEcGoodsPurchases();

    private GetEcGoodsPurchases() {
        super(new APITag[]{APITag.DGS}, "goods", "buyer", "firstIndex", "lastIndex", "withPublicFeedbacksOnly", "completed");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long goodsId = ParameterParser.getUnsignedLong(req, "goods", true);
        long buyerId = ParameterParser.getAccountId(req, "buyer", false);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        final boolean withPublicFeedbacksOnly = "true".equalsIgnoreCase(req.getParameter("withPublicFeedbacksOnly"));
        final boolean completed = "true".equalsIgnoreCase(req.getParameter("completed"));


        JSONObject response = new JSONObject();
        JSONArray purchasesJSON = new JSONArray();
        response.put("purchases", purchasesJSON);

        try (H2Iterator<ElectronicProductStore.Purchase> iterator = ElectronicProductStore.Purchase.getGoodsPurchases(goodsId,
                buyerId, withPublicFeedbacksOnly, completed, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                purchasesJSON.add(JSONData.purchase(iterator.next()));
            }
        }
        return response;
    }

}
