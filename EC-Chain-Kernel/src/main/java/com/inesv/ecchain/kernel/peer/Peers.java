package com.inesv.ecchain.kernel.peer;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.http.API;
import com.inesv.ecchain.kernel.http.APIEnum;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.DoSFilter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.springframework.stereotype.Component;

import javax.servlet.DispatcherType;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

@Component
public final class Peers {
    public static int ecmaxnumberofconnectedpublicpeers;
    static Set<String> ecknownBlacklistedPeers;
    static int ecconnectTimeout;
    static int ecreadTimeout;
    static int ecblacklistingPeriod;
    static boolean getecMorePeers;
    static boolean ecuseWebSockets;
    static int webecSocketIdleTimeout;
    static boolean isecGzipEnabled;
    static boolean ignoreecPeerAnnouncedAddress;
    static boolean cjdnsecOnly;
    static final ExecutorService peersService = new QueuedThreadPool(2, 15);
    private static List<String> wellKnownPeers;
    private static String myecPlatform;
    private static String myEcAddress;
    private static int myPeerServerPort;
    private static String myHallmark;
    private static boolean shareMyAddress;
    private static boolean enablePeerUPnP;
    private static int maxNumberOfInboundConnections;
    private static int maxNumberOfOutboundConnections;
    private static int maxNumberOfKnownPeers;
    private static int minNumberOfKnownPeers;
    private static boolean enableHallmarkProtection;
    private static int pushThreshold;
    private static int pullThreshold;
    private static int sendToPeersLimit;
    private static boolean usePeersDb;
    private static boolean savePeers;
    private static JSONObject myPeerInfo;
    private static List<PeerService> myServices;
    private static final ListenerManager<Peer, PeersEvent> LISTENER_MANAGER = new ListenerManager<>();
    private static final ConcurrentMap<String, PeerImpl> peers = new ConcurrentHashMap<>();
    static final Collection<PeerImpl> allPeers = Collections.unmodifiableCollection(peers.values());
    private static final ConcurrentMap<String, String> selfAnnouncedAddresses = new ConcurrentHashMap<>();
    private static final ExecutorService sendingService = Executors.newFixedThreadPool(10);
    private static int[] MAX_VERSION;
    static volatile int communicationLoggingMask;
    private static volatile PeerBlockchainState currentBlockchainState;
    private static volatile JSONStreamAware myPeerInfoRequest;
    private static volatile JSONStreamAware myPeerInfoResponse;
    private static final Runnable peerConnectingThread = new Runnable() {

        @Override
        public void run() {

            try {
                try {

                    final int now = new EcTime.EpochEcTime().getTime();
                    if (!hasEnoughConnectedPublicPeers(Peers.ecmaxnumberofconnectedpublicpeers)) {
                        List<Future<?>> futures = new ArrayList<>();
                        List<Peer> hallmarkedPeers = getPeers(peer -> !peer.isBlacklisted()
                                && peer.getAnnouncedAddress() != null
                                && peer.getState() != PeerState.CONNECTED
                                && now - peer.getLastConnectAttempt() > 600
                                && peer.providesService(PeerService.HALLMARK));
                        List<Peer> nonhallmarkedPeers = getPeers(peer -> !peer.isBlacklisted()
                                && peer.getAnnouncedAddress() != null
                                && peer.getState() != PeerState.CONNECTED
                                && now - peer.getLastConnectAttempt() > 600
                                && !peer.providesService(PeerService.HALLMARK));
                        if (!hallmarkedPeers.isEmpty() || !nonhallmarkedPeers.isEmpty()) {
                            Set<PeerImpl> connectSet = new HashSet<>();
                            for (int i = 0; i < 10; i++) {
                                List<Peer> peerList;
                                if (hallmarkedPeers.isEmpty()) {
                                    peerList = nonhallmarkedPeers;
                                } else if (nonhallmarkedPeers.isEmpty()) {
                                    peerList = hallmarkedPeers;
                                } else {
                                    peerList = (ThreadLocalRandom.current().nextInt(2) == 0 ? hallmarkedPeers : nonhallmarkedPeers);
                                }
                                connectSet.add((PeerImpl) peerList.get(ThreadLocalRandom.current().nextInt(peerList.size())));
                            }
                            connectSet.forEach(peer -> futures.add(peersService.submit(() -> {
                                peer.connect();
                                if (peer.getState() == PeerState.CONNECTED &&
                                        enableHallmarkProtection && peer.getPeerWeight() == 0 &&
                                        hasTooManyOutboundConnections()) {
                                    LoggerUtil.logDebug("Too many outbound connections, deactivating peer " + peer.getPeerHost());
                                    peer.deactivate();
                                }
                                return null;
                            })));
                            for (Future<?> future : futures) {
                                future.get();
                            }
                        }
                    }

                    peers.values().forEach(peer -> {
                        if (peer.getState() == PeerState.CONNECTED
                                && now - peer.getLastUpdated() > 3600
                                && now - peer.getLastConnectAttempt() > 600) {
                            peersService.submit(peer::connect);
                        }
                        if (peer.getLastInboundRequest() != 0 &&
                                now - peer.getLastInboundRequest() > Peers.webecSocketIdleTimeout / 1000) {
                            peer.setLastInboundRequest(0);
                            notifyListeners(peer, PeersEvent.REMOVE_INBOUND);
                        }
                    });

                    if (hasTooManyKnownPeers() && hasEnoughConnectedPublicPeers(Peers.ecmaxnumberofconnectedpublicpeers)) {
                        int initialSize = peers.size();
                        for (PeerImpl peer : peers.values()) {
                            if (now - peer.getLastUpdated() > 24 * 3600) {
                                peer.remove();
                            }
                            if (hasTooFewKnownPeers()) {
                                break;
                            }
                        }
                        if (hasTooManyKnownPeers()) {
                            PriorityQueue<PeerImpl> sortedPeers = new PriorityQueue<>(peers.values());
                            int skipped = 0;
                            while (skipped < Peers.minNumberOfKnownPeers) {
                                if (sortedPeers.poll() == null) {
                                    break;
                                }
                                skipped += 1;
                            }
                            while (!sortedPeers.isEmpty()) {
                                sortedPeers.poll().remove();
                            }
                        }
                        LoggerUtil.logDebug("Reduced peer pool size from " + initialSize + " to " + peers.size());
                    }

                    for (String wellKnownPeer : wellKnownPeers) {
                        PeerImpl peer = selectOrCreatePeer(wellKnownPeer, true);
                        if (peer != null && now - peer.getLastUpdated() > 3600 && now - peer.getLastConnectAttempt() > 600) {
                            peersService.submit(() -> {
                                addPeer(peer);
                                connectPeer(peer);
                            });
                        }
                    }

                } catch (Exception e) {
                    LoggerUtil.logError("Error connecting to peer", e);
                }
            } catch (Throwable t) {
                LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
                System.exit(1);
            }

        }

    };
    private static final Runnable peerUnBlacklistingThread = () -> {

        try {
            try {

                int curTime = new EcTime.EpochEcTime().getTime();
                for (PeerImpl peer : peers.values()) {
                    peer.updateBlacklistedStatus(curTime);
                }

            } catch (Exception e) {
                LoggerUtil.logError("Error un-blacklisting peer", e);
            }
        } catch (Throwable t) {
            LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }

    };
    private static final Runnable getMorePeersThread = new Runnable() {

        private final JSONStreamAware getPeersRequest;
        private volatile boolean updatedPeer;

        {
            JSONObject request = new JSONObject();
            request.put("requestType", "getPeers");
            getPeersRequest = JSON.prepareRequest(request);
        }

        @Override
        public void run() {

            try {
                try {
                    if (hasTooManyKnownPeers()) {
                        return;
                    }
                    Peer peer = getAnyPeer(PeerState.CONNECTED, true);
                    if (peer == null) {
                        return;
                    }
                    JSONObject response = peer.send(getPeersRequest, 10 * 1024 * 1024);
                    if (response == null) {
                        return;
                    }
                    JSONArray peers = (JSONArray) response.get("peers");
                    Set<String> addedAddresses = new HashSet<>();
                    if (peers != null) {
                        JSONArray services = (JSONArray) response.get("services");
                        boolean setServices = (services != null && services.size() == peers.size());
                        int now = new EcTime.EpochEcTime().getTime();
                        for (int i = 0; i < peers.size(); i++) {
                            String announcedAddress = (String) peers.get(i);
                            PeerImpl newPeer = selectOrCreatePeer(announcedAddress, true);
                            if (newPeer != null) {
                                if (now - newPeer.getLastUpdated() > 24 * 3600) {
                                    newPeer.setLastUpdated(now);
                                    updatedPeer = true;
                                }
                                if (Peers.addPeer(newPeer) && setServices) {
                                    newPeer.setServices(Long.parseUnsignedLong((String) services.get(i)));
                                }
                                addedAddresses.add(announcedAddress);
                                if (hasTooManyKnownPeers()) {
                                    break;
                                }
                            }
                        }
                        if (savePeers && updatedPeer) {
                            updateSavedPeers();
                            updatedPeer = false;
                        }
                    }

                    JSONArray myPeers = new JSONArray();
                    JSONArray myServices = new JSONArray();
                    Peers.getAllPeers().forEach(myPeer -> {
                        if (!myPeer.isBlacklisted() && myPeer.getAnnouncedAddress() != null
                                && myPeer.getState() == PeerState.CONNECTED && myPeer.shareAddress()
                                && !addedAddresses.contains(myPeer.getAnnouncedAddress())
                                && !myPeer.getAnnouncedAddress().equals(peer.getAnnouncedAddress())) {
                            myPeers.add(myPeer.getAnnouncedAddress());
                            myServices.add(Long.toUnsignedString(((PeerImpl) myPeer).getServices()));
                        }
                    });
                    if (myPeers.size() > 0) {
                        JSONObject request = new JSONObject();
                        request.put("requestType", "addPeers");
                        request.put("peers", myPeers);
                        request.put("services", myServices);            // Separate array for backwards compatibility
                        peer.send(JSON.prepareRequest(request), 0);
                    }

                } catch (Exception e) {
                    LoggerUtil.logError("Error requesting peers from a peer", e);
                }
            } catch (Throwable t) {
                LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
                System.exit(1);
            }

        }

        private void updateSavedPeers() {
            int now = new EcTime.EpochEcTime().getTime();
            //
            // Load the current database entries and map announced address to database entry
            //
            List<PeerH2.Entry> oldPeers = PeerH2.loadPeers();
            Map<String, PeerH2.Entry> oldMap = new HashMap<>(oldPeers.size());
            oldPeers.forEach(entry -> oldMap.put(entry.getAddress(), entry));
            //
            // Create the current peer map (note that there can be duplicate peer entries with
            // the same announced address)
            //
            Map<String, PeerH2.Entry> currentPeers = new HashMap<>();
            Peers.peers.values().forEach(peer -> {
                if (peer.getAnnouncedAddress() != null && !peer.isBlacklisted() && now - peer.getLastUpdated() < 7 * 24 * 3600) {
                    currentPeers.put(peer.getAnnouncedAddress(),
                            new PeerH2.Entry(peer.getAnnouncedAddress(), peer.getServices(), peer.getLastUpdated()));
                }
            });
            //
            // Build toDelete and toUpdate lists
            //
            List<PeerH2.Entry> toDelete = new ArrayList<>(oldPeers.size());
            oldPeers.forEach(entry -> {
                if (currentPeers.get(entry.getAddress()) == null) {
                    toDelete.add(entry);
                }
            });
            List<PeerH2.Entry> toUpdate = new ArrayList<>(currentPeers.size());
            currentPeers.values().forEach(entry -> {
                PeerH2.Entry oldEntry = oldMap.get(entry.getAddress());
                if (oldEntry == null || entry.getLastUpdated() - oldEntry.getLastUpdated() > 24 * 3600) {
                    toUpdate.add(entry);
                }
            });
            //
            // Nothing to do if all of the lists are empty
            //
            if (toDelete.isEmpty() && toUpdate.isEmpty()) {
                return;
            }
            //
            // Update the peer database
            //
            try {
                H2.H2.beginTransaction();
                PeerH2.deletePeers(toDelete);
                PeerH2.updatePeers(toUpdate);
                H2.H2.commitTransaction();
            } catch (Exception e) {
                H2.H2.rollbackTransaction();
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
        }

    };

    public static void start() {
        Init.init();
    }

    public static void shutdown() {
        if (Init.peerServer != null) {
            try {
                Init.peerServer.stop();
                if (enablePeerUPnP) {
                    Connector[] peerConnectors = Init.peerServer.getConnectors();
                    for (Connector peerConnector : peerConnectors) {
                        if (peerConnector instanceof ServerConnector) {
                            UPnP.delPort(((ServerConnector) peerConnector).getPort());
                        }
                    }
                }
            } catch (Exception e) {
                LoggerUtil.logError("Failed to stop peer server", e);
            }
        }
        ThreadPool.shutdownExecutor("sendingService", sendingService, 2);
        ThreadPool.shutdownExecutor("peersService", peersService, 5);
    }

    public static boolean addPeersListener(Listener<Peer> listener, PeersEvent eventType) {
        return Peers.LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removePeersListener(Listener<Peer> listener, PeersEvent eventType) {
        return Peers.LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    static void notifyListeners(Peer peer, PeersEvent eventType) {
        Peers.LISTENER_MANAGER.notify(peer, eventType);
    }

    public static int getDefaultPeerPort() {
        return Constants.EC_DEFAULT_PEER_PORT;
    }

    public static Collection<? extends Peer> getAllPeers() {
        return allPeers;
    }

    public static List<Peer> getActivePeers() {
        return getPeers(peer -> peer.getState() != PeerState.NON_CONNECTED);
    }

    public static List<Peer> getPeers(final PeerState state) {
        return getPeers(peer -> peer.getState() == state);
    }

    public static List<Peer> getPeers(Filter<Peer> filter) {
        return getPeers(filter, Integer.MAX_VALUE);
    }

    public static List<Peer> getPeers(Filter<Peer> filter, int limit) {
        List<Peer> result = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (filter.ok(peer)) {
                result.add(peer);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    public static Peer getPeer(String host) {
        return peers.get(host);
    }

    public static List<Peer> getInboundPeers() {
        return getPeers(Peer::isInbound);
    }

    public static boolean hasTooManyInboundPeers() {
        return getPeers(Peer::isInbound, maxNumberOfInboundConnections).size() >= maxNumberOfInboundConnections;
    }

    public static boolean hasTooManyOutboundConnections() {
        return getPeers(peer -> !peer.isBlacklisted() && peer.getState() == PeerState.CONNECTED && peer.getAnnouncedAddress() != null,
                maxNumberOfOutboundConnections).size() >= maxNumberOfOutboundConnections;
    }

    public static PeerImpl selectOrCreatePeer(String announcedAddress, boolean create) {
        if (announcedAddress == null) {
            return null;
        }
        announcedAddress = announcedAddress.trim().toLowerCase();
        PeerImpl peer;
        if ((peer = peers.get(announcedAddress)) != null) {
            return peer;
        }
        String host = selfAnnouncedAddresses.get(announcedAddress);
        if (host != null && (peer = peers.get(host)) != null) {
            return peer;
        }
        try {
            URI uri = new URI("http://" + announcedAddress);
            host = uri.getHost();
            if (host == null) {
                return null;
            }
            if ((peer = peers.get(host)) != null) {
                return peer;
            }
            String host2 = selfAnnouncedAddresses.get(host);
            if (host2 != null && (peer = peers.get(host2)) != null) {
                return peer;
            }
            InetAddress inetAddress = InetAddress.getByName(host);
            return selectOrCreatePeer(inetAddress, addressWithPort(announcedAddress), create);
        } catch (URISyntaxException | UnknownHostException e) {
            //LoggerUtil.logDebug("Invalid peer address: " + announcedAddress + ", " + e.toString());
            return null;
        }
    }

    static PeerImpl selectOrCreatePeer(String host) {
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            return selectOrCreatePeer(inetAddress, null, true);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    static PeerImpl selectOrCreatePeer(final InetAddress inetAddress, final String announcedAddress, final boolean create) {

        if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
            return null;
        }

        String host = inetAddress.getHostAddress();
        if (Peers.cjdnsecOnly && !host.substring(0, 2).equals("fc")) {
            return null;
        }
        //re-add the [] to ipv6 addresses lost in getHostAddress() above
        if (host.split(":").length > 2) {
            host = "[" + host + "]";
        }

        PeerImpl peer;
        if ((peer = peers.get(host)) != null) {
            return peer;
        }
        if (!create) {
            return null;
        }

        if (Peers.myEcAddress != null && Peers.myEcAddress.equalsIgnoreCase(announcedAddress)) {
            return null;
        }
        if (announcedAddress != null && announcedAddress.length() > Constants.EC_MAX_ANNOUNCED_ADDRESS_LENGTH) {
            return null;
        }
        peer = new PeerImpl(host, announcedAddress);
        if (peer.getPeerPort() == Constants.EC_TESTNET_PEER_PORT) {
            LoggerUtil.logDebug("Peer " + host + " is using testnet port " + peer.getPeerPort() + ", ignoring");
            return null;
        }
        return peer;
    }

    static void setAnnouncedAddress(PeerImpl peer, String newAnnouncedAddress) {
        Peer oldPeer = peers.get(peer.getPeerHost());
        if (oldPeer != null) {
            String oldAnnouncedAddress = oldPeer.getAnnouncedAddress();
            if (oldAnnouncedAddress != null && !oldAnnouncedAddress.equals(newAnnouncedAddress)) {
                LoggerUtil.logDebug("Removing old announced address " + oldAnnouncedAddress + " for peer " + oldPeer.getPeerHost());
                selfAnnouncedAddresses.remove(oldAnnouncedAddress);
            }
        }
        if (newAnnouncedAddress != null) {
            String oldHost = selfAnnouncedAddresses.put(newAnnouncedAddress, peer.getPeerHost());
            if (oldHost != null && !peer.getPeerHost().equals(oldHost)) {
                LoggerUtil.logDebug("Announced address " + newAnnouncedAddress + " now maps to peer " + peer.getPeerHost()
                        + ", removing old peer " + oldHost);
                oldPeer = peers.remove(oldHost);
                if (oldPeer != null) {
                    Peers.notifyListeners(oldPeer, PeersEvent.REMOVE);
                }
            }
        }
        peer.setAnnouncedAddress(newAnnouncedAddress);
    }

    public static boolean addPeer(Peer peer, String newAnnouncedAddress) {
        setAnnouncedAddress((PeerImpl) peer, newAnnouncedAddress.toLowerCase());
        return addPeer(peer);
    }

    public static boolean addPeer(Peer peer) {
        if (peers.put(peer.getPeerHost(), (PeerImpl) peer) == null) {
            LISTENER_MANAGER.notify(peer, PeersEvent.NEW_PEER);
            return true;
        }
        return false;
    }

    public static PeerImpl removePeer(Peer peer) {
        if (peer.getAnnouncedAddress() != null) {
            selfAnnouncedAddresses.remove(peer.getAnnouncedAddress());
        }
        return peers.remove(peer.getPeerHost());
    }

    public static void connectPeer(Peer peer) {
        peer.unBlacklist();
        ((PeerImpl) peer).connect();
    }

    public static void sendToSomePeers(EcBlock ecBlock) {
        JSONObject request = ecBlock.getJSONObject();
        request.put("requestType", "processBlock");
        sendToSomePeers(request);
    }

    public static void sendToSomePeers(List<? extends Transaction> transactions) {
        int nextBatchStart = 0;
        while (nextBatchStart < transactions.size()) {
            JSONObject request = new JSONObject();
            JSONArray transactionsData = new JSONArray();
            for (int i = nextBatchStart; i < nextBatchStart + Constants.SEND_TRANSACTIONS_BATCH_SIZE && i < transactions.size(); i++) {
                transactionsData.add(transactions.get(i).getJSONObject());
            }
            request.put("requestType", "processTransactions");
            request.put("transactions", transactionsData);
            sendToSomePeers(request);
            nextBatchStart += Constants.SEND_TRANSACTIONS_BATCH_SIZE;
        }
    }

    private static void sendToSomePeers(final JSONObject request) {
        sendingService.submit(() -> {
            final JSONStreamAware jsonRequest = JSON.prepareRequest(request);

            int successful = 0;
            List<Future<JSONObject>> expectedResponses = new ArrayList<>();
            for (final Peer peer : peers.values()) {

                if (Peers.enableHallmarkProtection && peer.getPeerWeight() < Peers.pushThreshold) {
                    continue;
                }

                if (!peer.isBlacklisted() && peer.getState() == PeerState.CONNECTED && peer.getAnnouncedAddress() != null
                        && peer.getBlockchainState() != PeerBlockchainState.LIGHT_CLIENT) {
                    Future<JSONObject> futureResponse = peersService.submit(() -> peer.send(jsonRequest));
                    expectedResponses.add(futureResponse);
                }
                if (expectedResponses.size() >= Peers.sendToPeersLimit - successful) {
                    for (Future<JSONObject> future : expectedResponses) {
                        try {
                            JSONObject response = future.get();
                            if (response != null && response.get("error") == null) {
                                successful += 1;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            LoggerUtil.logError("Error in sendToSomePeers", e);
                        }

                    }
                    expectedResponses.clear();
                }
                if (successful >= Peers.sendToPeersLimit) {
                    return;
                }
            }
        });
    }

    public static Peer getAnyPeer(final PeerState state, final boolean applyPullThreshold) {
        return getWeightedPeer(getPublicPeers(state, applyPullThreshold));
    }

    public static List<Peer> getPublicPeers(final PeerState state, final boolean applyPullThreshold) {
        return getPeers(peer -> !peer.isBlacklisted() && peer.getState() == state && peer.getAnnouncedAddress() != null
                && (!applyPullThreshold || !Peers.enableHallmarkProtection || peer.getPeerWeight() >= Peers.pullThreshold));
    }

    public static Peer getWeightedPeer(List<Peer> selectedPeers) {
        if (selectedPeers.isEmpty()) {
            return null;
        }
        if (!Peers.enableHallmarkProtection || ThreadLocalRandom.current().nextInt(3) == 0) {
            return selectedPeers.get(ThreadLocalRandom.current().nextInt(selectedPeers.size()));
        }
        long totalWeight = 0;
        for (Peer peer : selectedPeers) {
            long weight = peer.getPeerWeight();
            if (weight == 0) {
                weight = 1;
            }
            totalWeight += weight;
        }
        long hit = ThreadLocalRandom.current().nextLong(totalWeight);
        for (Peer peer : selectedPeers) {
            long weight = peer.getPeerWeight();
            if (weight == 0) {
                weight = 1;
            }
            if ((hit -= weight) < 0) {
                return peer;
            }
        }
        return null;
    }

    static String addressWithPort(String address) {
        if (address == null) {
            return null;
        }
        try {
            URI uri = new URI("http://" + address);
            String host = uri.getHost();
            int port = uri.getPort();
            return port > 0 && port != Peers.getDefaultPeerPort() ? host + ":" + port : host;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static boolean isOldVersion(String version, int[] minVersion) {
        if (version == null) {
            return true;
        }
        if (version.endsWith("e")) {
            version = version.substring(0, version.length() - 1);
        }
        String[] versions = version.split("\\.");
        for (int i = 0; i < minVersion.length && i < versions.length; i++) {
            try {
                int v = Integer.parseInt(versions[i]);
                if (v > minVersion[i]) {
                    return false;
                } else if (v < minVersion[i]) {
                    return true;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return versions.length < minVersion.length;
    }

    public static boolean isNewVersion(String version) {
        if (version == null) {
            return true;
        }
        if (version.endsWith("e")) {
            version = version.substring(0, version.length() - 1);
        }
        String[] versions = version.split("\\.");
        for (int i = 0; i < MAX_VERSION.length && i < versions.length; i++) {
            try {
                int v = Integer.parseInt(versions[i]);
                if (v > MAX_VERSION[i]) {
                    return true;
                } else if (v < MAX_VERSION[i]) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return versions.length > MAX_VERSION.length;
    }

    public static boolean hasTooFewKnownPeers() {
        return peers.size() < Peers.minNumberOfKnownPeers;
    }

    public static boolean hasTooManyKnownPeers() {
        return peers.size() > Peers.maxNumberOfKnownPeers;
    }

    private static boolean hasEnoughConnectedPublicPeers(int limit) {
        return getPeers(peer -> !peer.isBlacklisted() && peer.getState() == PeerState.CONNECTED && peer.getAnnouncedAddress() != null
                && (!Peers.enableHallmarkProtection || peer.getPeerWeight() > 0), limit).size() >= limit;
    }

    public static boolean setCommunicationLoggingMask(String[] events) {
        boolean updated = true;
        int mask = 0;
        if (events != null) {
            for (String event : events) {
                switch (event) {
                    case "EXCEPTION":
                        mask |= Constants.EC_LOGGING_MASK_EXCEPTIONS;
                        break;
                    case "HTTP-ERROR":
                        mask |= Constants.EC_LOGGING_MASK_NON200_RESPONSES;
                        break;
                    case "HTTP-OK":
                        mask |= Constants.EC_LOGGING_MASK_200_RESPONSES;
                        break;
                    default:
                        updated = false;
                }
                if (!updated) {
                    break;
                }
            }
        }
        if (updated) {
            communicationLoggingMask = mask;
        }
        return updated;
    }

    public static List<PeerService> getServices() {
        return myServices;
    }

    private static void checkBlockchainState() {
        EcBlockchain bc = EcBlockchainImpl.getInstance();
        PeerBlockchainState state = Constants.IS_LIGHT_CLIENT ? PeerBlockchainState.LIGHT_CLIENT :
                (EcBlockchainProcessorImpl.getInstance().isDownloading() || bc.getLastBlockTimestamp() < new EcTime.EpochEcTime().getTime() - 600) ? PeerBlockchainState.DOWNLOADING :
                        (bc.getLastECBlock().getBaseTarget() / Constants.EC_INITIAL_BASE_TARGET > 10) ? PeerBlockchainState.FORK :
                                PeerBlockchainState.UP_TO_DATE;
        if (state != currentBlockchainState) {
            JSONObject json = new JSONObject(myPeerInfo);
            json.put("blockchainState", state.ordinal());
            myPeerInfoResponse = JSON.prepare(json);
            json.put("requestType", "getInfo");
            myPeerInfoRequest = JSON.prepareRequest(json);
            currentBlockchainState = state;
        }
    }

    public static JSONStreamAware getMyPeerInfoRequest() {
        checkBlockchainState();
        return myPeerInfoRequest;
    }

    public static JSONStreamAware getMyPeerInfoResponse() {
        checkBlockchainState();
        return myPeerInfoResponse;
    }

    public static PeerBlockchainState getMyBlockchainState() {
        checkBlockchainState();
        return currentBlockchainState;
    }

    private static class Init {

        private static Server peerServer;

        static {
            if (Peers.shareMyAddress) {
                peerServer = new Server();
                ServerConnector connector = new ServerConnector(peerServer);
                final int port = Peers.myPeerServerPort;
                connector.setPort(port);
                final String host = PropertiesUtil.getKeyForString("ec.peerServerHost", null);
                connector.setHost(host);
                connector.setIdleTimeout(PropertiesUtil.getKeyForInt("ec.peerServerIdleTimeout", 0));
                connector.setReuseAddress(true);
                peerServer.addConnector(connector);

                ServletContextHandler ctxHandler = new ServletContextHandler();
                ctxHandler.setContextPath("/");

                ServletHolder peerServletHolder = new ServletHolder(new PeerServlet());
                ctxHandler.addServlet(peerServletHolder, "/*");

                if (PropertiesUtil.getKeyForBoolean("ec.enablePeerServerDoSFilter")) {
                    FilterHolder dosFilterHolder = ctxHandler.addFilter(DoSFilter.class, "/*",
                            EnumSet.of(DispatcherType.REQUEST));
                    dosFilterHolder.setInitParameter("maxRequestsPerSec", PropertiesUtil.getKeyForString("ec.peerServerDoSFilter.maxRequestsPerSec", null));
                    dosFilterHolder.setInitParameter("delayMs", PropertiesUtil.getKeyForString("ec.peerServerDoSFilter.delayMs", null));
                    dosFilterHolder.setInitParameter("maxRequestMs", PropertiesUtil.getKeyForString("ec.peerServerDoSFilter.maxRequestMs", null));
                    dosFilterHolder.setInitParameter("trackSessions", "false");
                    dosFilterHolder.setAsyncSupported(true);
                }

                if (isecGzipEnabled) {
                    GzipHandler gzipHandler = new GzipHandler();
                    gzipHandler.setIncludedMethods("GET", "POST");
                    gzipHandler.setIncludedPaths("/*");
                    gzipHandler.setMinGzipSize(Constants.EC_MIN_COMPRESS_SIZE);
                    ctxHandler.setGzipHandler(gzipHandler);
                }

                peerServer.setHandler(ctxHandler);
                peerServer.setStopAtShutdown(true);
                ThreadPool.runBeforeStart(() -> {
                    try {
                        if (enablePeerUPnP) {
                            Connector[] peerConnectors = peerServer.getConnectors();
                            for (Connector peerConnector : peerConnectors) {
                                if (peerConnector instanceof ServerConnector)
                                    UPnP.addPort(((ServerConnector) peerConnector).getPort());
                            }
                        }
                        peerServer.start();
                        LoggerUtil.logInfo("Started peer networking server at " + host + ":" + port);
                    } catch (Exception e) {
                        LoggerUtil.logError("Failed to start peer networking server", e);
                        throw new RuntimeException(e.toString(), e);
                    }
                }, true);
            } else {
                peerServer = null;
                LoggerUtil.logInfo("shareMyAddress is disabled, will not start peer networking server");
            }
        }

        private Init() {
        }

        private static void init() {
        }

    }

    static {
        Peers.addPeersListener(peer -> peersService.submit(() -> {
            if (peer.getAnnouncedAddress() != null && !peer.isBlacklisted()) {
                try {
                    H2.H2.beginTransaction();
                    PeerH2.updatePeer((PeerImpl) peer);
                    H2.H2.commitTransaction();
                } catch (RuntimeException e) {
                    LoggerUtil.logError("Unable to update peer database", e);
                    H2.H2.rollbackTransaction();
                } finally {
                    H2.H2.endTransaction();
                }
            }
        }), PeersEvent.CHANGED_SERVICES);

        Account.addListener(account -> peers.values().forEach(peer -> {
            if (peer.getQualityProof() != null && peer.getQualityProof().getAccountId() == account.getId()) {
                Peers.LISTENER_MANAGER.notify(peer, PeersEvent.WEIGHT);
            }
        }), com.inesv.ecchain.kernel.core.Event.BALANCE);

        if (!Constants.IS_OFFLINE) {
            ThreadPool.scheduleThread("PeerConnecting", Peers.peerConnectingThread, 20);
            ThreadPool.scheduleThread("PeerUnBlacklisting", Peers.peerUnBlacklistingThread, 60);
            if (Peers.getecMorePeers) {
                ThreadPool.scheduleThread("GetMorePeers", Peers.getMorePeersThread, 20);
            }
        }

        String version = Constants.EC_VERSION;
        if (version.endsWith("e")) {
            version = version.substring(0, version.length() - 1);
        }
        String[] versions = version.split("\\.");
        MAX_VERSION = new int[versions.length];
        for (int i = 0; i < versions.length; i++) {
            MAX_VERSION[i] = Integer.parseInt(versions[i]);
        }
        String platform = PropertiesUtil.getKeyForString("ec.myPlatform", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        if (platform.length() > Constants.EC_MAX_PLATFORM_LENGTH) {
            platform = platform.substring(0, Constants.EC_MAX_PLATFORM_LENGTH);
        }
        myecPlatform = platform;
        myEcAddress = Convert.emptyToNull(PropertiesUtil.getKeyForString("ec.myAddress", "").trim());
        if (myEcAddress != null && myEcAddress.endsWith(":" + Constants.EC_TESTNET_PEER_PORT)) {
            throw new RuntimeException("Port " + Constants.EC_TESTNET_PEER_PORT + " should only be used for testnet!!!");
        }
        String myHost = null;
        int myPort = -1;
        if (myEcAddress != null) {
            try {
                URI uri = new URI("http://" + myEcAddress);
                myHost = uri.getHost();
                myPort = (uri.getPort() == -1 ? Peers.getDefaultPeerPort() : uri.getPort());
                InetAddress[] myAddrs = InetAddress.getAllByName(myHost);
                boolean addrValid = false;
                Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
                chkAddr:
                while (intfs.hasMoreElements()) {
                    NetworkInterface intf = intfs.nextElement();
                    List<InterfaceAddress> intfAddrs = intf.getInterfaceAddresses();
                    for (InterfaceAddress intfAddr : intfAddrs) {
                        InetAddress extAddr = intfAddr.getAddress();
                        for (InetAddress myAddr : myAddrs) {
                            if (extAddr.equals(myAddr)) {
                                addrValid = true;
                                break chkAddr;
                            }
                        }
                    }
                }
                if (!addrValid) {
                    InetAddress extAddr = UPnP.getExternalAddress();
                    if (extAddr != null) {
                        for (InetAddress myAddr : myAddrs) {
                            if (extAddr.equals(myAddr)) {
                                addrValid = true;
                                break;
                            }
                        }
                    }
                }
                if (!addrValid) {
                    LoggerUtil.logInfo("Your announced address does not match your external address");
                }
            } catch (SocketException e) {
                LoggerUtil.logError("Unable to enumerate the network interfaces :" + e.toString());
            } catch (URISyntaxException | UnknownHostException e) {
                LoggerUtil.logInfo("Your announced address is not valid: " + e.toString());
            }
        }
        myPeerServerPort = PropertiesUtil.getKeyForInt("ec.peerServerPort", 0);
        if (myPeerServerPort == Constants.EC_TESTNET_PEER_PORT) {
            throw new RuntimeException("Port " + Constants.EC_TESTNET_PEER_PORT + " should only be used for testnet!!!");
        }
        shareMyAddress = PropertiesUtil.getKeyForBoolean("ec.shareMyAddress") && !Constants.IS_OFFLINE;//true
        enablePeerUPnP = PropertiesUtil.getKeyForBoolean("ec.enablePeerUPnP");//true
        myHallmark = Convert.emptyToNull(PropertiesUtil.getKeyForString("ec.myHallmark", "").trim());//null
        if (Peers.myHallmark != null && Peers.myHallmark.length() > 0) {//false
            try {
                QualityProof qualityProof = QualityProof.parseHallmark(Peers.myHallmark);
                if (!qualityProof.isValid()) {
                    throw new RuntimeException("QualityProof is not valid");
                }
                if (myEcAddress != null) {
                    if (!qualityProof.getHost().equals(myHost)) {
                        throw new RuntimeException("Invalid qualityProof host");
                    }
                    if (myPort != qualityProof.getPort()) {
                        throw new RuntimeException("Invalid qualityProof port");
                    }
                }
            } catch (RuntimeException e) {
                LoggerUtil.logError("Your hallmark is invalid: " + Peers.myHallmark + " for your address: " + myEcAddress);
                throw new RuntimeException(e.toString(), e);
            }
        }
        List<PeerService> servicesList = new ArrayList<>();
        JSONObject json = new JSONObject();
        if (myEcAddress != null) {
            try {
                URI uri = new URI("http://" + myEcAddress);
                String host = uri.getHost();
                int port = uri.getPort();
                String announcedAddress;
                if (port >= 0) {
                    announcedAddress = myEcAddress;
                } else {
                    announcedAddress = host + (myPeerServerPort != Constants.EC_DEFAULT_PEER_PORT ? ":" + myPeerServerPort : "");
                }
                if (announcedAddress == null || announcedAddress.length() > Constants.EC_MAX_ANNOUNCED_ADDRESS_LENGTH) {
                    throw new RuntimeException("Invalid announced address length: " + announcedAddress);
                }
                json.put("announcedAddress", announcedAddress);
            } catch (URISyntaxException e) {
                LoggerUtil.logInfo("Your announce address is invalid: " + myEcAddress);
                throw new RuntimeException(e.toString(), e);
            }
        }
        if (Peers.myHallmark != null && Peers.myHallmark.length() > 0) {
            json.put("hallmark", Peers.myHallmark);
            servicesList.add(PeerService.HALLMARK);
        }
        json.put("application", Constants.EC_APPLICATION);
        json.put("version", Constants.EC_VERSION);
        json.put("platform", Peers.myecPlatform);
        json.put("shareAddress", Peers.shareMyAddress);
        if (!Constants.EC_ENABLE_PRUNING && Constants.INCLUDE_EXPIRED_PRUNABLE) {
            servicesList.add(PeerService.PRUNABLE);
        }
        if (API.openecapiport > 0) {
            json.put("apiPort", API.openecapiport);
            servicesList.add(PeerService.API);
        }
        if (API.openecapisslport > 0) {
            json.put("apiSSLPort", API.openecapisslport);
            servicesList.add(PeerService.API_SSL);
        }

        if (API.openecapiport > 0 || API.openecapisslport > 0) {
            EnumSet<APIEnum> disabledAPISet = EnumSet.noneOf(APIEnum.class);

            API.disabledAPIs.forEach(apiName -> {
                APIEnum api = APIEnum.fromEcName(apiName);
                if (api != null) {
                    disabledAPISet.add(api);
                }
            });
            API.disabledAPITags.forEach(apiTag -> {
                for (APIEnum api : APIEnum.values()) {
                    if (api.getAPIHandler() != null && api.getAPIHandler().getAPITags().contains(apiTag)) {
                        disabledAPISet.add(api);
                    }
                }
            });
            json.put("disabledAPIs", APIEnum.enumSetToBase64ECString(disabledAPISet));

            json.put("apiServerIdleTimeout", API.apiServerIdleTimeout);

            if (API.apiservercors) {
                servicesList.add(PeerService.CORS);
            }
        }

        long services = 0;
        for (PeerService service : servicesList) {
            services |= service.getCode();
        }
        json.put("services", Long.toUnsignedString(services));
        myServices = Collections.unmodifiableList(servicesList);
        LoggerUtil.logDebug("My peer info:\n" + json.toJSONString());
        myPeerInfo = json;
        final List<String> defaultPeers =  PropertiesUtil.getStringListProperty("ec.defaultPeers");
        wellKnownPeers = Collections.unmodifiableList(PropertiesUtil.getStringListProperty("ec.wellKnownPeers"));

        List<String> knownBlacklistedPeersList = PropertiesUtil.getStringListProperty("ec.knownBlacklistedPeers");
        if (knownBlacklistedPeersList.isEmpty()) {
            ecknownBlacklistedPeers = Collections.emptySet();
        } else {
            ecknownBlacklistedPeers = Collections.unmodifiableSet(new HashSet<>(knownBlacklistedPeersList));
        }

        maxNumberOfInboundConnections = PropertiesUtil.getKeyForInt("ec.maxNumberOfInboundConnections", 0);
        maxNumberOfOutboundConnections = PropertiesUtil.getKeyForInt("ec.maxNumberOfOutboundConnections", 0);
        ecmaxnumberofconnectedpublicpeers = Math.min(PropertiesUtil.getKeyForInt("ec.maxNumberOfConnectedPublicPeers", 0),
                maxNumberOfOutboundConnections);
        maxNumberOfKnownPeers = PropertiesUtil.getKeyForInt("ec.maxNumberOfKnownPeers", 0);
        minNumberOfKnownPeers = PropertiesUtil.getKeyForInt("ec.minNumberOfKnownPeers", 0);
        ecconnectTimeout = PropertiesUtil.getKeyForInt("ec.connectTimeout", 0);
        ecreadTimeout = PropertiesUtil.getKeyForInt("ec.readTimeout", 0);
        enableHallmarkProtection = PropertiesUtil.getKeyForBoolean("ec.enableHallmarkProtection") && !Constants.IS_LIGHT_CLIENT;
        pushThreshold = PropertiesUtil.getKeyForInt("ec.pushThreshold", 0);
        pullThreshold = PropertiesUtil.getKeyForInt("ec.pullThreshold", 0);
        ecuseWebSockets = PropertiesUtil.getKeyForBoolean("ec.useWebSockets");
        webecSocketIdleTimeout = PropertiesUtil.getKeyForInt("ec.webSocketIdleTimeout", 0);
        isecGzipEnabled = PropertiesUtil.getKeyForBoolean("ec.enablePeerServerGZIPFilter");
        ecblacklistingPeriod = PropertiesUtil.getKeyForInt("ec.blacklistingPeriod", 0) / 1000;
        communicationLoggingMask = PropertiesUtil.getKeyForInt("ec.communicationLoggingMask", 0);
        sendToPeersLimit = PropertiesUtil.getKeyForInt("ec.sendToPeersLimit", 0);
        usePeersDb = PropertiesUtil.getKeyForBoolean("ec.usePeersDb") && !Constants.IS_OFFLINE;
        savePeers = usePeersDb && PropertiesUtil.getKeyForBoolean("ec.savePeers");
        getecMorePeers = PropertiesUtil.getKeyForBoolean("ec.getMorePeers");
        cjdnsecOnly = PropertiesUtil.getKeyForBoolean("ec.cjdnsOnly");
        ignoreecPeerAnnouncedAddress = PropertiesUtil.getKeyForBoolean("ec.ignorePeerAnnouncedAddress");
        if (ecuseWebSockets && Constants.USE_PROXY) {
            LoggerUtil.logInfo("Using a proxy, will not create outbound websockets.");
        }

        final List<Future<String>> unresolvedPeers = Collections.synchronizedList(new ArrayList<>());

        if (!Constants.IS_OFFLINE) {
            ThreadPool.runBeforeStart(new Runnable() {

                private final Set<PeerH2.Entry> entries = new HashSet<>();

                @Override
                public void run() {
                    final int now = new EcTime.EpochEcTime().getTime();
                    wellKnownPeers.forEach(address -> entries.add(new PeerH2.Entry(address, 0, now)));
                    if (usePeersDb) {
                        LoggerUtil.logDebug("Loading known peers from the database...");
                        defaultPeers.forEach(address -> entries.add(new PeerH2.Entry(address, 0, now)));
                        if (savePeers) {
                            List<PeerH2.Entry> dbPeers = PeerH2.loadPeers();
                            dbPeers.forEach(entry -> {
                                if (!entries.add(entry)) {
                                    // Database entries override entries from ec.properties
                                    entries.remove(entry);
                                    entries.add(entry);
                                }
                            });
                        }
                    }
                    entries.forEach(entry -> {
                        Future<String> unresolvedAddress = peersService.submit(() -> {
                            PeerImpl peer = Peers.selectOrCreatePeer(entry.getAddress(), true);
                            if (peer != null) {
                                peer.setLastUpdated(entry.getLastUpdated());
                                peer.setServices(entry.getServices());
                                Peers.addPeer(peer);
                                return null;
                            }
                            return entry.getAddress();
                        });
                        unresolvedPeers.add(unresolvedAddress);
                    });
                }
            }, false);
        }

        ThreadPool.runAfterStart(() -> {
            for (Future<String> unresolvedPeer : unresolvedPeers) {
                try {
                    String badAddress = unresolvedPeer.get(5, TimeUnit.SECONDS);
                    if (badAddress != null) {
                        LoggerUtil.logDebug("Failed to resolve peer address: " + badAddress);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    LoggerUtil.logError("Failed to add peer", e);
                } catch (TimeoutException ignore) {
                }
            }
            LoggerUtil.logDebug("Known peers: " + peers.size());
        });

    }
}
