package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchain;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.FoundryMachine;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetNextBlockGeneratorsTemp extends APIRequestHandler {

    static final GetNextBlockGeneratorsTemp instance = new GetNextBlockGeneratorsTemp();

    private GetNextBlockGeneratorsTemp() {
        super(new APITag[]{APITag.FORGING}, "limit");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        JSONObject response = new JSONObject();
        int limit = Math.max(1, ParameterParser.getInt(req, "limit", 1, Integer.MAX_VALUE, false));
        EcBlockchain ecBlockchain = EcBlockchainImpl.getInstance();
        ecBlockchain.readECLock();
        try {
            EcBlock lastEcBlock = ecBlockchain.getLastECBlock();
            response.put("timestamp", lastEcBlock.getTimestamp());
            response.put("height", lastEcBlock.getHeight());
            response.put("lastBlock", Long.toUnsignedString(lastEcBlock.getECId()));
            List<FoundryMachine.ActiveGenerator> activeGenerators = FoundryMachine.getNextGenerators();
            response.put("activeCount", activeGenerators.size());
            JSONArray generators = new JSONArray();
            for (FoundryMachine.ActiveGenerator generator : activeGenerators) {
                if (generator.getHitTime() > Integer.MAX_VALUE) {
                    break;
                }
                JSONObject resp = new JSONObject();
                JSONData.putAccount(resp, "account", generator.getAccountId());
                resp.put("effectiveBalanceEC", generator.getEffectiveBalance());
                resp.put("hitTime", generator.getHitTime());
                resp.put("deadline", (int) generator.getHitTime() - lastEcBlock.getTimestamp());
                generators.add(resp);
                if (generators.size() == limit) {
                    break;
                }
            }
            response.put("generators", generators);
        } finally {
            ecBlockchain.readECUnlock();
        }
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }
}
