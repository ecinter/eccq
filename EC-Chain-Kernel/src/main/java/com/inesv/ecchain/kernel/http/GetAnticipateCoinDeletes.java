package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetAnticipateCoinDeletes extends APIRequestHandler {

    static final GetAnticipateCoinDeletes instance = new GetAnticipateCoinDeletes();

    private GetAnticipateCoinDeletes() {
        super(new APITag[]{APITag.AE}, "asset", "account", "includeAssetInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long assetId = ParameterParser.getUnsignedLong(req, "asset", false);
        long accountId = ParameterParser.getAccountId(req, "account", false);
        boolean includeAssetInfo = "true".equalsIgnoreCase(req.getParameter("includeAssetInfo"));

        Filter<Transaction> filter = transaction -> {
            if (transaction.getTransactionType() != ColoredCoins.ASSET_DELETE) {
                return false;
            }
            if (accountId != 0 && transaction.getSenderId() != accountId) {
                return false;
            }
            Mortgaged.ColoredCoinsAssetDelete attachment = (Mortgaged.ColoredCoinsAssetDelete) transaction.getAttachment();
            return assetId == 0 || attachment.getAssetId() == assetId;
        };

        List<? extends Transaction> transactions = EcBlockchainImpl.getInstance().getExpectedTransactions(filter);

        JSONObject response = new JSONObject();
        JSONArray deletesData = new JSONArray();
        transactions.forEach(transaction -> deletesData.add(JSONData.expectedAssetDelete(transaction, includeAssetInfo)));
        response.put("deletes", deletesData);

        return response;
    }

}
