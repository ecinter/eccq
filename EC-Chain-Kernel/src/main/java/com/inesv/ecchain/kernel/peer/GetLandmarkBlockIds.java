package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchain;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class GetLandmarkBlockIds extends PeerRequestHandler {

    static final GetLandmarkBlockIds instance = new GetLandmarkBlockIds();

    private GetLandmarkBlockIds() {
    }


    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {

        JSONObject response = new JSONObject();
        try {

            JSONArray milestoneBlockIds = new JSONArray();

            String lastBlockIdString = (String) request.get("lastBlockId");
            if (lastBlockIdString != null) {
                long lastBlockId = Convert.parseUnsignedLong(lastBlockIdString);
                EcBlockchain bc = EcBlockchainImpl.getInstance();
                long myLastBlockId = bc.getLastECBlock().getECId();
                if (myLastBlockId == lastBlockId || EcBlockchainImpl.getInstance().hasBlock(lastBlockId)) {
                    milestoneBlockIds.add(lastBlockIdString);
                    response.put("milestoneBlockIds", milestoneBlockIds);
                    if (myLastBlockId == lastBlockId) {
                        response.put("last", Boolean.TRUE);
                    }
                    return response;
                }
            }

            long blockId;
            int height;
            int jump;
            int limit = 10;
            int blockchainHeight = EcBlockchainImpl.getInstance().getHeight();
            String lastMilestoneBlockIdString = (String) request.get("lastMilestoneBlockId");
            if (lastMilestoneBlockIdString != null) {
                EcBlock lastMilestoneEcBlock = EcBlockchainImpl.getInstance().getBlock(Convert.parseUnsignedLong(lastMilestoneBlockIdString));
                if (lastMilestoneEcBlock == null) {
                    throw new IllegalStateException("Don't have block " + lastMilestoneBlockIdString);
                }
                height = lastMilestoneEcBlock.getHeight();
                jump = Math.min(1440, Math.max(blockchainHeight - height, 1));
                height = Math.max(height - jump, 0);
            } else if (lastBlockIdString != null) {
                height = blockchainHeight;
                jump = 10;
            } else {
                peer.blacklist("Old getMilestoneBlockIds request");
                response.put("error", "Old getMilestoneBlockIds protocol not supported, please upgrade");
                return response;
            }
            blockId = EcBlockchainImpl.getInstance().getBlockIdAtHeight(height);

            while (height > 0 && limit-- > 0) {
                milestoneBlockIds.add(Long.toUnsignedString(blockId));
                blockId = EcBlockchainImpl.getInstance().getBlockIdAtHeight(height);
                height = height - jump;
            }
            response.put("milestoneBlockIds", milestoneBlockIds);

        } catch (RuntimeException e) {
            LoggerUtil.logDebug(e.toString());
            return PeerServlet.error(e);
        }

        return response;
    }

    @Override
    boolean rejectRequest() {
        return true;
    }

}
