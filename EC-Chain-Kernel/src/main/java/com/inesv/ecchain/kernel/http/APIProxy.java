package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.common.util.ThreadPool;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.Peers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class APIProxy {
    public static final Set<String> NOT_FORWARDED_REQUESTS;
    static final boolean ENABLE_API_PROXY = Constants.IS_LIGHT_CLIENT ||
            (PropertiesUtil.getKeyForBoolean("ec.enableAPIProxy") && API.openecapiport == 0 && API.openecapisslport == 0);
    private static final APIProxy instance = new APIProxy();
    private static final Runnable PEERS_UPDATE_THREAD = () -> {
        int curTime = new EcTime.EpochEcTime().getTime();
        instance.stringIntegerConcurrentHashMap.entrySet().removeIf((entry) -> {
            if (entry.getValue() < curTime) {
                LoggerUtil.logDebug("Unblacklisting API peer " + entry.getKey());
                return true;
            }
            return false;
        });
        List<String> currentPeersHosts = instance.peersHosts;
        if (currentPeersHosts != null) {
            for (String host : currentPeersHosts) {
                Peer peer = Peers.getPeer(host);
                if (peer != null) {
                    Peers.connectPeer(peer);
                }
            }
        }
    };

    static {
        Set<String> requests = new HashSet<>();
        requests.add("getBlockchainStatus");
        requests.add("getState");

        final EnumSet<APITag> notForwardedTags = EnumSet.of(APITag.DEBUG, APITag.NETWORK);

        for (APIEnum api : APIEnum.values()) {
            APIRequestHandler handler = api.getAPIHandler();
            if (handler.requireBlockchain() && !Collections.disjoint(handler.getAPITags(), notForwardedTags)) {
                requests.add(api.getAPIEnumName());
            }
        }

        NOT_FORWARDED_REQUESTS = Collections.unmodifiableSet(requests);
    }

    static {
        if (!Constants.IS_OFFLINE && ENABLE_API_PROXY) {
            ThreadPool.scheduleThread("APIProxyPeersUpdate", PEERS_UPDATE_THREAD, 60);
        }
    }

    private final ConcurrentHashMap<String, Integer> stringIntegerConcurrentHashMap = new ConcurrentHashMap<>();
    private volatile String forcedPeerHost;
    private volatile List<String> peersHosts = Collections.emptyList();
    private volatile String mainPeerAnnouncedAddress;

    private APIProxy() {

    }

    public static void start() {
    }

    public static APIProxy getInstance() {
        return instance;
    }

    static boolean isActivated() {
        return Constants.IS_LIGHT_CLIENT || (ENABLE_API_PROXY && EcBlockchainProcessorImpl.getInstance().isDownloading());
    }

    Peer getEcServingPeer(String requestType) {
        if (forcedPeerHost != null) {
            return Peers.getPeer(forcedPeerHost);
        }

        APIEnum requestAPI = APIEnum.fromEcName(requestType);
        if (!peersHosts.isEmpty()) {
            for (String host : peersHosts) {
                Peer peer = Peers.getPeer(host);
                if (peer != null && peer.isApiConnectable() && !peer.getDisabledAPIs().contains(requestAPI)) {
                    return peer;
                }
            }
        }

        List<Peer> connectablePeers = Peers.getPeers(p -> p.isApiConnectable() && !stringIntegerConcurrentHashMap.containsKey(p.getPeerHost()));
        if (connectablePeers.isEmpty()) {
            return null;
        }
        // subset of connectable peers that have at least one new API enabled, which was disabled for the
        // The first peer (element 0 of peersHosts) is chosen at random. Next peers are chosen randomly from a
        // previously chosen peers. In worst case the size of peersHosts will be the number of APIs
        Peer peer = getRandomAPIPeer(connectablePeers);
        if (peer == null) {
            return null;
        }

        Peer resultPeer = null;
        List<String> currentPeersHosts = new ArrayList<>();
        EnumSet<APIEnum> disabledAPIs = EnumSet.noneOf(APIEnum.class);
        currentPeersHosts.add(peer.getPeerHost());
        mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
        if (!peer.getDisabledAPIs().contains(requestAPI)) {
            resultPeer = peer;
        }
        while (!disabledAPIs.isEmpty() && !connectablePeers.isEmpty()) {
            // remove all peers that do not introduce new enabled APIs
            connectablePeers.removeIf(p -> p.getDisabledAPIs().containsAll(disabledAPIs));
            peer = getRandomAPIPeer(connectablePeers);
            if (peer != null) {
                currentPeersHosts.add(peer.getPeerHost());
                if (!peer.getDisabledAPIs().contains(requestAPI)) {
                    resultPeer = peer;
                }
                disabledAPIs.retainAll(peer.getDisabledAPIs());
            }
        }
        peersHosts = Collections.unmodifiableList(currentPeersHosts);
        LoggerUtil.logInfo("Selected API peer " + resultPeer + " peer hosts selected " + currentPeersHosts);
        return resultPeer;
    }

    Peer setEcForcedPeer(Peer peer) {
        if (peer != null) {
            forcedPeerHost = peer.getPeerHost();
            mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
            return peer;
        } else {
            forcedPeerHost = null;
            mainPeerAnnouncedAddress = null;
            return getEcServingPeer(null);
        }
    }

    String getEcMainPeerAnnouncedAddress() {
        // The first client request GetBlockchainState is handled by the server
        // Not by the proxy. In order to report a peer to the client we have
        // To select some initial peer.
        if (mainPeerAnnouncedAddress == null) {
            Peer peer = getEcServingPeer(null);
            if (peer != null) {
                mainPeerAnnouncedAddress = peer.getAnnouncedAddress();
            }
        }
        return mainPeerAnnouncedAddress;
    }

    void blacklistHost(String host) {
        stringIntegerConcurrentHashMap.put(host, new EcTime.EpochEcTime().getTime() + Constants.BLACKLISTING_PERIOD);
        if (peersHosts.contains(host)) {
            peersHosts = Collections.emptyList();
            getEcServingPeer(null);
        }
    }

    private Peer getRandomAPIPeer(List<Peer> peers) {
        if (peers.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(peers.size());
        return peers.remove(index);
    }
}
