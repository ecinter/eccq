package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.PeerService;
import com.inesv.ecchain.kernel.peer.PeerState;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

public final class GetPeers extends APIRequestHandler {

    static final GetPeers instance = new GetPeers();

    private GetPeers() {
        super(new APITag[]{APITag.NETWORK}, "active", "state", "service", "service", "service", "includePeerInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        boolean active = "true".equalsIgnoreCase(req.getParameter("active"));
        String stateValue = Convert.emptyToNull(req.getParameter("state"));
        String[] serviceValues = req.getParameterValues("service");
        boolean includePeerInfo = "true".equalsIgnoreCase(req.getParameter("includePeerInfo"));
        PeerState state;
        if (stateValue != null) {
            try {
                state = PeerState.valueOf(stateValue);
            } catch (RuntimeException exc) {
                return JSONResponses.incorrect("state", "- '" + stateValue + "' is not defined");
            }
        } else {
            state = null;
        }
        long serviceCodes = 0;
        if (serviceValues != null) {
            for (String serviceValue : serviceValues) {
                try {
                    serviceCodes |= PeerService.valueOf(serviceValue).getCode();
                } catch (RuntimeException exc) {
                    return JSONResponses.incorrect("service", "- '" + serviceValue + "' is not defined");
                }
            }
        }

        Collection<? extends Peer> peers = active ? Peers.getActivePeers() : state != null ? Peers.getPeers(state) : Peers.getAllPeers();
        JSONArray peersJSON = new JSONArray();
        if (serviceCodes != 0) {
            final long services = serviceCodes;
            if (includePeerInfo) {
                peers.forEach(peer -> {
                    if (peer.providesServices(services)) {
                        peersJSON.add(JSONData.peer(peer));
                    }
                });
            } else {
                peers.forEach(peer -> {
                    if (peer.providesServices(services)) {
                        peersJSON.add(peer.getPeerHost());
                    }
                });
            }
        } else {
            if (includePeerInfo) {
                peers.forEach(peer -> peersJSON.add(JSONData.peer(peer)));
            } else {
                peers.forEach(peer -> peersJSON.add(peer.getPeerHost()));
            }
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
