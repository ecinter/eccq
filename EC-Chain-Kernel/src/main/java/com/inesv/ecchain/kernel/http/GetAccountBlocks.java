package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountBlocks extends APIRequestHandler {

    static final GetAccountBlocks instance = new GetAccountBlocks();

    private GetAccountBlocks() {
        super(new APITag[]{APITag.ACCOUNTS}, "account", "timestamp", "firstIndex", "lastIndex", "includeTransactions");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long accountId = ParameterParser.getAccountId(req, true);
        int timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));

        JSONArray blocks = new JSONArray();
        try (H2Iterator<? extends EcBlock> iterator = EcBlockchainImpl.getInstance().getBlocks(accountId, timestamp, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                EcBlock ecBlock = iterator.next();
                blocks.add(JSONData.block(ecBlock, includeTransactions, false));
            }
        }

        JSONObject response = new JSONObject();
        response.put("blocks", blocks);

        return response;
    }

}
