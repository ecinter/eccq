package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.PropertyDelete;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCoinDeletes extends APIRequestHandler {

    static final GetCoinDeletes instance = new GetCoinDeletes();

    private GetCoinDeletes() {
        super(new APITag[]{APITag.AE}, "asset", "account", "firstIndex", "lastIndex", "timestamp", "includeAssetInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long assetId = ParameterParser.getUnsignedLong(req, "asset", false);
        long accountId = ParameterParser.getAccountId(req, false);
        if (assetId == 0 && accountId == 0) {
            return JSONResponses.MISSING_ASSET_ACCOUNT;
        }
        int timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeAssetInfo = "true".equalsIgnoreCase(req.getParameter("includeAssetInfo"));

        JSONObject response = new JSONObject();
        JSONArray deletesData = new JSONArray();
        H2Iterator<PropertyDelete> deletes = null;
        try {
            if (accountId == 0) {
                deletes = PropertyDelete.getAssetDeletes(assetId, firstIndex, lastIndex);
            } else if (assetId == 0) {
                deletes = PropertyDelete.getAccountAssetDeletes(accountId, firstIndex, lastIndex);
            } else {
                deletes = PropertyDelete.getAccountAssetDeletes(accountId, assetId, firstIndex, lastIndex);
            }
            while (deletes.hasNext()) {
                PropertyDelete propertyDelete = deletes.next();
                if (propertyDelete.getTimestamp() < timestamp) {
                    break;
                }
                deletesData.add(JSONData.assetDelete(propertyDelete, includeAssetInfo));
            }
        } finally {
            H2Utils.h2close(deletes);
        }
        response.put("deletes", deletesData);

        return response;
    }

    @Override
    protected boolean startDbTransaction() {
        return true;
    }
}
