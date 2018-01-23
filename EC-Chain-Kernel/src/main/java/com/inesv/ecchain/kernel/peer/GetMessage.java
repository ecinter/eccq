package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class GetMessage extends PeerRequestHandler {

    static final GetMessage instance = new GetMessage();

    private static final JSONStreamAware INVALID_ANNOUNCED_ADDRESS;

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.INVALID_ANNOUNCED_ADDRESS);
        INVALID_ANNOUNCED_ADDRESS = JSON.prepare(response);
    }

    private GetMessage() {
    }

    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {
        PeerImpl peerImpl = (PeerImpl) peer;
        peerImpl.setLastUpdated(new EcTime.EpochEcTime().getTime());
        long origServices = peerImpl.getServices();
        String servicesString = (String) request.get("services");
        peerImpl.setServices(servicesString != null ? Long.parseUnsignedLong(servicesString) : 0);
        peerImpl.analyzeHallmark((String) request.get("hallmark"));
        if (!Peers.ignoreecPeerAnnouncedAddress) {
            String announcedAddress = Convert.emptyToNull((String) request.get("announcedAddress"));
            if (announcedAddress != null) {
                announcedAddress = Peers.addressWithPort(announcedAddress.toLowerCase());
                if (announcedAddress != null) {
                    if (!peerImpl.verifyAnnouncedAddress(announcedAddress)) {
                        LoggerUtil.logDebug("GetMessage: ignoring invalid announced address for " + peerImpl.getPeerHost());
                        if (!peerImpl.verifyAnnouncedAddress(peerImpl.getAnnouncedAddress())) {
                            LoggerUtil.logDebug("GetMessage: old announced address for " + peerImpl.getPeerHost() + " no longer valid");
                            Peers.setAnnouncedAddress(peerImpl, null);
                        }
                        peerImpl.setPeerState(PeerState.NON_CONNECTED);
                        return INVALID_ANNOUNCED_ADDRESS;
                    }
                    if (!announcedAddress.equals(peerImpl.getAnnouncedAddress())) {
                        LoggerUtil.logDebug("GetMessage: peer " + peer.getPeerHost() + " changed announced address from " + peer.getAnnouncedAddress() + " to " + announcedAddress);
                        int oldPort = peerImpl.getPeerPort();
                        Peers.setAnnouncedAddress(peerImpl, announcedAddress);
                        if (peerImpl.getPeerPort() != oldPort) {
                            // force checking connectivity to new announced port
                            peerImpl.setPeerState(PeerState.NON_CONNECTED);
                        }
                    }
                } else {
                    Peers.setAnnouncedAddress(peerImpl, null);
                }
            }
        }
        String application = (String) request.get("application");
        if (application == null) {
            application = "?";
        }
        peerImpl.setPeerApplication(application.trim());

        String version = (String) request.get("version");
        if (version == null) {
            version = "?";
        }
        peerImpl.setPeerVersion(version.trim());

        String platform = (String) request.get("platform");
        if (platform == null) {
            platform = "?";
        }
        peerImpl.setPeerPlatform(platform.trim());

        peerImpl.setPeerShareAddress(Boolean.TRUE.equals(request.get("shareAddress")));

        peerImpl.setApiPort(request.get("apiPort"));
        peerImpl.setApiSSLPort(request.get("apiSSLPort"));
        peerImpl.setDisabledAPIs(request.get("disabledAPIs"));
        peerImpl.setApiServerIdleTimeout(request.get("apiServerIdleTimeout"));
        peerImpl.setPeerBlockchainState(request.get("blockchainState"));

        if (peerImpl.getServices() != origServices) {
            Peers.notifyListeners(peerImpl, PeersEvent.CHANGED_SERVICES);
        }

        return Peers.getMyPeerInfoResponse();

    }

    @Override
    boolean rejectRequest() {
        return false;
    }

}
