package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class BlacklistPeer extends APIRequestHandler {

    static final BlacklistPeer instance = new BlacklistPeer();

    private BlacklistPeer() {
        super(new APITag[]{APITag.NETWORK}, "peer");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request)
            throws EcException {
        JSONObject response = new JSONObject();

        String peerAddress = request.getParameter("peer");
        if (peerAddress == null) {
            return JSONResponses.MISSING_PEER;
        }
        Peer peer = Peers.selectOrCreatePeer(peerAddress, true);
        if (peer == null) {
            return JSONResponses.UNKNOWN_PEER;
        } else {
            Peers.addPeer(peer);
            peer.blacklist("Manual blacklist");
            response.put("done", true);
        }

        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
