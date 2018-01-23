package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetECBlock extends APIRequestHandler {

    static final GetECBlock instance = new GetECBlock();

    private GetECBlock() {
        super(new APITag[]{APITag.BLOCKS}, "timestamp");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        int timestamp = ParameterParser.getTimestamp(req);
        if (timestamp == 0) {
            timestamp = new EcTime.EpochEcTime().getTime();
        }
        EcBlock ecEcBlock = EcBlockchainImpl.getInstance().getECBlock(timestamp);
        JSONObject response = new JSONObject();
        response.put("ecBlockId", ecEcBlock.getStringECId());
        response.put("ecBlockHeight", ecEcBlock.getHeight());
        response.put("timestamp", timestamp);
        return response;
    }

}