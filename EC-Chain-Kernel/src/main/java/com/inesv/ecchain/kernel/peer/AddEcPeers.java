package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class AddEcPeers extends PeerRequestHandler {

    static final AddEcPeers instance = new AddEcPeers();

    private AddEcPeers() {
    }

    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {
        final JSONArray peers = (JSONArray) request.get("peers");
        if (peers != null && Peers.getecMorePeers && !Peers.hasTooManyKnownPeers()) {
            final JSONArray services = (JSONArray) request.get("services");
            final boolean setServices = (services != null && services.size() == peers.size());
            Peers.peersService.submit(() -> {
                for (int i = 0; i < peers.size(); i++) {
                    String announcedAddress = (String) peers.get(i);
                    PeerImpl newPeer = Peers.selectOrCreatePeer(announcedAddress, true);
                    if (newPeer != null) {
                        if (Peers.addPeer(newPeer) && setServices) {
                            newPeer.setServices(Long.parseUnsignedLong((String) services.get(i)));
                        }
                        if (Peers.hasTooManyKnownPeers()) {
                            break;
                        }
                    }
                }
            });
        }
        return JSON.EMPTY_JSON;
    }

    @Override
    boolean rejectRequest() {
        return false;
    }

}
