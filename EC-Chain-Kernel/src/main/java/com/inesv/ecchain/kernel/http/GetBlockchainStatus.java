package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;

public final class GetBlockchainStatus extends APIRequestHandler {

    static final GetBlockchainStatus instance = new GetBlockchainStatus();

    private GetBlockchainStatus() {
        super(new APITag[]{APITag.BLOCKS, APITag.INFO});
    }

    @Override
    protected JSONObject processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        response.put("application", Constants.EC_APPLICATION);
        response.put("version", Constants.EC_VERSION);
        response.put("time", new EcTime.EpochEcTime().getTime());
        EcBlock lastEcBlock = EcBlockchainImpl.getInstance().getLastECBlock();
        response.put("lastBlock", lastEcBlock.getStringECId());
        response.put("cumulativeDifficulty", lastEcBlock.getCumulativeDifficulty().toString());
        response.put("numberOfBlocks", lastEcBlock.getHeight() + 1);
        EcBlockchainProcessor ecBlockchainProcessor = EcBlockchainProcessorImpl.getInstance();
        Peer lastBlockchainFeeder = ecBlockchainProcessor.getLastECBlockchainFeeder();
        response.put("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
        response.put("lastBlockchainFeederHeight", ecBlockchainProcessor.getLastECBlockchainFeederHeight());
        response.put("isScanning", ecBlockchainProcessor.isScanning());
        response.put("isDownloading", ecBlockchainProcessor.isDownloading());
        response.put("maxRollback", Constants.MAX_ROLLBACK);
        response.put("currentMinRollbackHeight", EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight());
        response.put("isTestnet",false);
        response.put("MAX_PRUNABLE_LIFETIME", Constants.EC_MAX_PRUNABLE_LIFETIME);
        response.put("includeExpiredPrunable", Constants.INCLUDE_EXPIRED_PRUNABLE);
        response.put("EC_CORRECT_INVALID_FEES", Constants.EC_CORRECT_INVALID_FEES);
        response.put("ledgerTrimKeep", Constants.TRIM_KEEP);
        JSONArray servicesArray = new JSONArray();
        Peers.getServices().forEach(service -> servicesArray.add(service.name()));
        response.put("services", servicesArray);
        if (APIProxy.isActivated()) {
            String servingPeer = APIProxy.getInstance().getEcMainPeerAnnouncedAddress();
            response.put("apiProxy", true);
            response.put("apiProxyPeer", servingPeer);
        } else {
            response.put("apiProxy", false);
        }
        response.put("IS_LIGHT_CLIENT", Constants.IS_LIGHT_CLIENT);
        response.put("maxAPIRecords", API.maxRecords);
        response.put("blockchainState", Peers.getMyBlockchainState());
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
