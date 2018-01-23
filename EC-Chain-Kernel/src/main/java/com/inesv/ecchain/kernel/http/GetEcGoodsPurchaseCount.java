package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcGoodsPurchaseCount extends APIRequestHandler {

    static final GetEcGoodsPurchaseCount instance = new GetEcGoodsPurchaseCount();

    private GetEcGoodsPurchaseCount() {
        super(new APITag[]{APITag.DGS}, "goods", "withPublicFeedbacksOnly", "completed");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long goodsId = ParameterParser.getUnsignedLong(req, "goods", true);
        final boolean withPublicFeedbacksOnly = "true".equalsIgnoreCase(req.getParameter("withPublicFeedbacksOnly"));
        final boolean completed = "true".equalsIgnoreCase(req.getParameter("completed"));

        JSONObject response = new JSONObject();
        response.put("numberOfPurchases", ElectronicProductStore.Purchase.getGoodsPurchaseCount(goodsId, withPublicFeedbacksOnly, completed));
        return response;

    }

}
