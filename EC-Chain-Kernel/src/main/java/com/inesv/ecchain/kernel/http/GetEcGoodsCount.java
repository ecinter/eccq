package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcGoodsCount extends APIRequestHandler {

    static final GetEcGoodsCount instance = new GetEcGoodsCount();

    private GetEcGoodsCount() {
        super(new APITag[]{APITag.DGS}, "seller", "inStockOnly");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long sellerId = ParameterParser.getAccountId(req, "seller", false);
        boolean inStockOnly = !"false".equalsIgnoreCase(req.getParameter("inStockOnly"));

        JSONObject response = new JSONObject();
        response.put("numberOfGoods", sellerId != 0
                ? ElectronicProductStore.Goods.getSellerGoodsCount(sellerId, inStockOnly)
                : inStockOnly ? ElectronicProductStore.Goods.getCountInStock() : ElectronicProductStore.Goods.getCount());
        return response;
    }

}
