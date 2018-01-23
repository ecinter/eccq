package com.inesv.ecchain.kernel.peer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class GetEcPeers extends PeerRequestHandler {

    static final GetEcPeers instance = new GetEcPeers();

    private GetEcPeers() {
    }

    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        JSONArray services = new JSONArray();
        Peers.getAllPeers().forEach(otherPeer -> {
            if (!otherPeer.isBlacklisted() && otherPeer.getAnnouncedAddress() != null
                    && otherPeer.getState() == PeerState.CONNECTED && otherPeer.shareAddress()) {
                jsonArray.add(otherPeer.getAnnouncedAddress());
                services.add(Long.toUnsignedString(((PeerImpl) otherPeer).getServices()));
            }
        });
        response.put("peers", jsonArray);
        response.put("services", services);         // Separate array for backwards compatibility
        return response;
    }

    @Override
    boolean rejectRequest() {
        return false;
    }

}
