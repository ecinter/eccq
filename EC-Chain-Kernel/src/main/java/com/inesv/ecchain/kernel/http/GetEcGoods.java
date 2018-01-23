package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import com.inesv.ecchain.kernel.H2.FilteringIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcGoods extends APIRequestHandler {

    static final GetEcGoods instance = new GetEcGoods();

    private GetEcGoods() {
        super(new APITag[]{APITag.DGS}, "seller", "firstIndex", "lastIndex", "inStockOnly", "hideDelisted", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long sellerId = ParameterParser.getAccountId(req, "seller", false);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean inStockOnly = !"false".equalsIgnoreCase(req.getParameter("inStockOnly"));
        boolean hideDelisted = "true".equalsIgnoreCase(req.getParameter("hideDelisted"));
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));

        JSONObject response = new JSONObject();
        JSONArray goodsJSON = new JSONArray();
        response.put("goods", goodsJSON);

        Filter<ElectronicProductStore.Goods> filter = hideDelisted ? goods -> !goods.isDelisted() : goods -> true;

        FilteringIterator<ElectronicProductStore.Goods> iterator = null;
        try {
            H2Iterator<ElectronicProductStore.Goods> goods;
            if (sellerId == 0) {
                if (inStockOnly) {
                    goods = ElectronicProductStore.Goods.getGoodsInStock(0, -1);
                } else {
                    goods = ElectronicProductStore.Goods.getAllGoods(0, -1);
                }
            } else {
                goods = ElectronicProductStore.Goods.getSellerGoods(sellerId, inStockOnly, 0, -1);
            }
            iterator = new FilteringIterator<>(goods, filter, firstIndex, lastIndex);
            while (iterator.hasNext()) {
                ElectronicProductStore.Goods good = iterator.next();
                goodsJSON.add(JSONData.goods(good, includeCounts));
            }
        } finally {
            H2Utils.h2close(iterator);
        }

        return response;
    }

}
