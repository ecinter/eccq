package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountProperty extends APIRequestHandler {

    static final GetAccountProperty instance = new GetAccountProperty();

    private GetAccountProperty() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.AE}, "account", "asset", "height", "includeAssetInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        int height = ParameterParser.getHeight(req);
        long assetId = ParameterParser.getUnsignedLong(req, "asset", false);
        boolean includeAssetInfo = "true".equalsIgnoreCase(req.getParameter("includeAssetInfo"));

        if (assetId == 0) {
            JSONObject response = new JSONObject();
            try (H2Iterator<Account.AccountPro> accountAssets = Account.getAccountPropertys(accountId, height, 0, -1)) {
                JSONArray assetJSON = new JSONArray();
                while (accountAssets.hasNext()) {
                    assetJSON.add(JSONData.accountAsset(accountAssets.next(), false, includeAssetInfo));
                }
                response.put("accountAssets", assetJSON);
                return response;
            }
        } else {
            Account.AccountPro accountPro = Account.getAccountProperty(accountId, assetId, height);
            if (accountPro != null) {
                return JSONData.accountAsset(accountPro, false, includeAssetInfo);
            }
            return JSON.EMPTY_JSON;
        }
    }

}
