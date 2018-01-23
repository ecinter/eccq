package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.ArrayList;
import java.util.List;

final class GetNextBlocks extends PeerRequestHandler {

    static final GetNextBlocks instance = new GetNextBlocks();

    static final JSONStreamAware TOO_MANY_BLOCKS_REQUESTED;

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.TOO_MANY_BLOCKS_REQUESTED);
        TOO_MANY_BLOCKS_REQUESTED = JSON.prepare(response);
    }

    private GetNextBlocks() {
    }


    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {

        JSONObject response = new JSONObject();
        JSONArray nextBlocksArray = new JSONArray();
        List<? extends EcBlock> blocks;
        long blockId = Convert.parseUnsignedLong((String) request.get("blockId"));
        List<String> stringList = (List<String>) request.get("blockIds");
        if (stringList != null) {
            if (stringList.size() > 36) {
                return TOO_MANY_BLOCKS_REQUESTED;
            }
            List<Long> idList = new ArrayList<>();
            stringList.forEach(stringId -> idList.add(Convert.parseUnsignedLong(stringId)));
            blocks = EcBlockchainImpl.getInstance().getBlocksAfter(blockId, idList);
        } else {
            long limit = Convert.parseLong(request.get("limit"));
            if (limit > 36) {
                return TOO_MANY_BLOCKS_REQUESTED;
            }
            blocks = EcBlockchainImpl.getInstance().getBlocksAfter(blockId, limit > 0 ? (int) limit : 36);
        }
        blocks.forEach(block -> nextBlocksArray.add(block.getJSONObject()));
        response.put("nextBlocks", nextBlocksArray);

        return response;
    }

    @Override
    boolean rejectRequest() {
        return true;
    }

}
