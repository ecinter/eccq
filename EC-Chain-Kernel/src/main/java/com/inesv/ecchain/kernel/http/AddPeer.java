package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class AddPeer extends APIRequestHandler {

    static final AddPeer instance = new AddPeer();

    private AddPeer() {
        super(new APITag[]{APITag.NETWORK}, "peer");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request)
            throws EcException {
        String peerAddress = Convert.emptyToNull(request.getParameter("peer"));
        if (peerAddress == null) {
            return JSONResponses.MISSING_PEER;
        }
        JSONObject response = new JSONObject();
        Peer peer = Peers.selectOrCreatePeer(peerAddress, true);
        if (peer != null) {
            boolean isNewlyAdded = Peers.addPeer(peer, peerAddress);
            Peers.connectPeer(peer);
            response = JSONData.peer(peer);
            response.put("isNewlyAdded", isNewlyAdded);
        } else {
            response.put("errorCode", 8);
            response.put("errorDescription", "Failed to add peer");
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
