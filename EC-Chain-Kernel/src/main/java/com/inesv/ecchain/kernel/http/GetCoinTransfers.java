package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.PropertyTransfer;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCoinTransfers extends APIRequestHandler {

    static final GetCoinTransfers instance = new GetCoinTransfers();

    private GetCoinTransfers() {
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
        JSONArray transfersData = new JSONArray();
        H2Iterator<PropertyTransfer> transfers = null;
        try {
            if (accountId == 0) {
                transfers = PropertyTransfer.getAssetTransfers(assetId, firstIndex, lastIndex);
            } else if (assetId == 0) {
                transfers = PropertyTransfer.getAccountAssetTransfers(accountId, firstIndex, lastIndex);
            } else {
                transfers = PropertyTransfer.getAccountAssetTransfers(accountId, assetId, firstIndex, lastIndex);
            }
            while (transfers.hasNext()) {
                PropertyTransfer propertyTransfer = transfers.next();
                if (propertyTransfer.getTimestamp() < timestamp) {
                    break;
                }
                transfersData.add(JSONData.assetTransfer(propertyTransfer, includeAssetInfo));
            }
        } finally {
            H2Utils.h2close(transfers);
        }
        response.put("transfers", transfersData);

        return response;
    }

    @Override
    protected boolean startDbTransaction() {
        return true;
    }
}
