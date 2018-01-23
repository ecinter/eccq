package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.List;

final class GetNextBlockIds extends PeerRequestHandler {

    static final GetNextBlockIds instance = new GetNextBlockIds();

    private GetNextBlockIds() {
    }


    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {

        JSONObject response = new JSONObject();

        JSONArray nextBlockIds = new JSONArray();
        long blockId = Convert.parseUnsignedLong((String) request.get("blockId"));
        int limit = (int) Convert.parseLong(request.get("limit"));
        if (limit > 1440) {
            return GetNextBlocks.TOO_MANY_BLOCKS_REQUESTED;
        }
        List<Long> ids = EcBlockchainImpl.getInstance().getBlockIdsAfter(blockId, limit > 0 ? limit : 1440);
        ids.forEach(id -> nextBlockIds.add(Long.toUnsignedString(id)));
        response.put("nextBlockIds", nextBlockIds);

        return response;
    }

    @Override
    boolean rejectRequest() {
        return true;
    }

}
