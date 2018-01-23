package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountBlockCount extends APIRequestHandler {

    static final GetAccountBlockCount instance = new GetAccountBlockCount();

    private GetAccountBlockCount() {
        super(new APITag[]{APITag.ACCOUNTS}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        JSONObject response = new JSONObject();
        response.put("numberOfBlocks", EcBlockchainImpl.getInstance().getECBlockCount(accountId));

        return response;
    }

}
