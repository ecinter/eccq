package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class GetCumulativeDifficulty extends PeerRequestHandler {

    static final GetCumulativeDifficulty instance = new GetCumulativeDifficulty();

    private GetCumulativeDifficulty() {
    }


    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {

        JSONObject response = new JSONObject();

        EcBlock lastEcBlock = EcBlockchainImpl.getInstance().getLastECBlock();
        response.put("cumulativeDifficulty", lastEcBlock.getCumulativeDifficulty().toString());
        response.put("blockchainHeight", lastEcBlock.getHeight());
        return response;
    }

    @Override
    boolean rejectRequest() {
        return true;
    }

}
