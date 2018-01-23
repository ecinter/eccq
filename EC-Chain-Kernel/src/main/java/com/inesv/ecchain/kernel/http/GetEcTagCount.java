package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEcTagCount extends APIRequestHandler {

    static final GetEcTagCount instance = new GetEcTagCount();

    private GetEcTagCount() {
        super(new APITag[]{APITag.DGS}, "inStockOnly");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        final boolean inStockOnly = !"false".equalsIgnoreCase(req.getParameter("inStockOnly"));

        JSONObject response = new JSONObject();
        response.put("numberOfTags", inStockOnly ? ElectronicProductStore.Tag.getCountInStock() : ElectronicProductStore.Tag.getCount());
        return response;
    }

}
