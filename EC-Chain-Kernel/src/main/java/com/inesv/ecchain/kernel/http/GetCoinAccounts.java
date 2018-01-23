package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCoinAccounts extends APIRequestHandler {

    static final GetCoinAccounts instance = new GetCoinAccounts();

    private GetCoinAccounts() {
        super(new APITag[]{APITag.AE}, "asset", "height", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long assetId = ParameterParser.getUnsignedLong(req, "asset", true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        int height = ParameterParser.getHeight(req);

        JSONArray accountAssets = new JSONArray();
        try (H2Iterator<Account.AccountPro> iterator = Account.getPropertyAccounts(assetId, height, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                Account.AccountPro accountPro = iterator.next();
                accountAssets.add(JSONData.accountAsset(accountPro, true, false));
            }
        }

        JSONObject response = new JSONObject();
        response.put("accountAssets", accountAssets);
        return response;

    }

}
