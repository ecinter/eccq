package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountBlockIds extends APIRequestHandler {

    static final GetAccountBlockIds instance = new GetAccountBlockIds();

    private GetAccountBlockIds() {
        super(new APITag[]{APITag.ACCOUNTS}, "account", "timestamp", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        int timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray blockIds = new JSONArray();
        try (H2Iterator<? extends EcBlock> iterator = EcBlockchainImpl.getInstance().getBlocks(accountId, timestamp, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                EcBlock ecBlock = iterator.next();
                blockIds.add(ecBlock.getStringECId());
            }
        }

        JSONObject response = new JSONObject();
        response.put("blockIds", blockIds);

        return response;
    }

}
