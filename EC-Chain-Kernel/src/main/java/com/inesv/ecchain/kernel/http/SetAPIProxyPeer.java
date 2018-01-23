package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.PeerState;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public class SetAPIProxyPeer extends APIRequestHandler {

    static final SetAPIProxyPeer instance = new SetAPIProxyPeer();

    private SetAPIProxyPeer() {
        super(new APITag[]{APITag.NETWORK}, "peer");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws EcException {
        String peerAddress = Convert.emptyToNull(request.getParameter("peer"));
        if (peerAddress == null) {
            Peer peer = APIProxy.getInstance().setEcForcedPeer(null);
            if (peer == null) {
                return API_PROXY_NO_OPEN_API_PEERS;
            }
            return JSONData.peer(peer);
        }
        Peer peer = Peers.selectOrCreatePeer(peerAddress, false);
        if (peer == null) {
            return UNKNOWN_PEER;
        }
        if (peer.getState() != PeerState.CONNECTED) {
            return PEER_NOT_CONNECTED;
        }
        if (!peer.isOpenAPI()) {
            return PEER_NOT_OPEN_API;
        }
        APIProxy.getInstance().setEcForcedPeer(peer);
        return JSONData.peer(peer);
    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }


}
