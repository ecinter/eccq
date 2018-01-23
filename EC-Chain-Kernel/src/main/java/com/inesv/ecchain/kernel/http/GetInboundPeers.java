package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetInboundPeers extends APIRequestHandler {

    static final GetInboundPeers instance = new GetInboundPeers();

    private GetInboundPeers() {
        super(new APITag[]{APITag.NETWORK}, "includePeerInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        boolean includePeerInfo = "true".equalsIgnoreCase(req.getParameter("includePeerInfo"));
        List<Peer> peers = Peers.getInboundPeers();
        JSONArray peersJSON = new JSONArray();
        if (includePeerInfo) {
            peers.forEach(peer -> peersJSON.add(JSONData.peer(peer)));
        } else {
            peers.forEach(peer -> peersJSON.add(peer.getPeerHost()));
        }
        JSONObject response = new JSONObject();
        response.put("peers", peersJSON);
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
