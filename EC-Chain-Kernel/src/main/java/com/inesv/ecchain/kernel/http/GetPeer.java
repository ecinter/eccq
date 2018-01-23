package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.MISSING_PEER;
import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_PEER;


public final class GetPeer extends APIRequestHandler {

    static final GetPeer instance = new GetPeer();

    private GetPeer() {
        super(new APITag[]{APITag.NETWORK}, "peer");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        String peerAddress = req.getParameter("peer");
        if (peerAddress == null) {
            return MISSING_PEER;
        }

        Peer peer = Peers.selectOrCreatePeer(peerAddress, false);
        if (peer == null) {
            return UNKNOWN_PEER;
        }

        return JSONData.peer(peer);

    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
