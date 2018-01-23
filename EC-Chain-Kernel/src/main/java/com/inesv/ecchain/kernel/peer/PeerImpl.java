package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcIOException;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.BlockOutOfOrderException;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.http.API;
import com.inesv.ecchain.kernel.http.APIEnum;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.zip.GZIPInputStream;

final class PeerImpl implements Peer {
    private volatile PeerState state;
    private volatile long downloadedVolume;
    private volatile long uploadedVolume;
    private volatile int lastUpdated;
    private volatile int lastConnectAttempt;
    private volatile int lastInboundRequest;
    private volatile long hallmarkBalance = -1;
    private final String host;
    private final PeerWebSocket peerWebSocket;
    private volatile PeerWebSocket inpeerWebSocket;
    private volatile int blacklistingTime;
    private volatile String blacklistingCause;
    private volatile boolean useWebSocket;
    private volatile String announcedAddress;
    private volatile int port;
    private volatile boolean shareAddress;
    private volatile int apiPort;
    private volatile int apiSSLPort;
    private volatile EnumSet<APIEnum> disabledAPIs;
    private volatile int apiServerIdleTimeout;
    private volatile String version;
    private volatile QualityProof qualityProof;
    private volatile String platform;
    private volatile String application;
    private volatile boolean isOldVersion;
    private volatile long adjustedWeight;
    private volatile int hallmarkBalanceHeight;
    private volatile long services;
    private volatile PeerBlockchainState blockchainState;

    PeerImpl(String host, String announcedAddress) {
        this.host = host;
        this.announcedAddress = announcedAddress;
        try {
            this.port = new URI("http://" + announcedAddress).getPort();
        } catch (URISyntaxException ignore) {
        }
        this.state = PeerState.NON_CONNECTED;
        this.shareAddress = true;
        this.peerWebSocket = new PeerWebSocket();
        this.useWebSocket = Peers.ecuseWebSockets && !Constants.USE_PROXY;
        this.disabledAPIs = EnumSet.noneOf(APIEnum.class);
        this.apiServerIdleTimeout = API.apiServerIdleTimeout;
        this.blockchainState = PeerBlockchainState.UP_TO_DATE;
    }

    @Override
    public String getPeerHost() {
        return host;
    }

    @Override
    public PeerState getState() {
        return state;
    }

    @Override
    public long getDownloadedVolume() {
        return downloadedVolume;
    }

    @Override
    public long getUploadedVolume() {
        return uploadedVolume;
    }

    @Override
    public String getPeerVersion() {
        return version;
    }

    @Override
    public String getApplication() {
        return application;
    }

    @Override
    public String getPlatform() {
        return platform;
    }

    @Override
    public String getSoftware() {
        return Convert.truncate(application, "?", 10, false)
                + " (" + Convert.truncate(version, "?", 10, false) + ")"
                + " @ " + Convert.truncate(platform, "?", 10, false);
    }

    @Override
    public int getApiPort() {
        return apiPort;
    }

    @Override
    public int getApiSSLPort() {
        return apiSSLPort;
    }

    @Override
    public Set<APIEnum> getDisabledAPIs() {
        return Collections.unmodifiableSet(disabledAPIs);
    }

    @Override
    public int getApiServerIdleTimeout() {
        return apiServerIdleTimeout;
    }

    @Override
    public PeerBlockchainState getBlockchainState() {
        return blockchainState;
    }

    @Override
    public boolean shareAddress() {
        return shareAddress;
    }

    @Override
    public String getAnnouncedAddress() {
        return announcedAddress;
    }

    @Override
    public int getPeerPort() {
        return port <= 0 ? Peers.getDefaultPeerPort() : port;
    }

    @Override
    public QualityProof getQualityProof() {
        return qualityProof;
    }

    @Override
    public int getPeerWeight() {
        if (qualityProof == null) {
            return 0;
        }
        if (hallmarkBalance == -1 || hallmarkBalanceHeight < EcBlockchainImpl.getInstance().getHeight() - 60) {
            long accountId = qualityProof.getAccountId();
            Account account = Account.getAccount(accountId);
            hallmarkBalance = account == null ? 0 : account.getBalanceNQT();
            hallmarkBalanceHeight = EcBlockchainImpl.getInstance().getHeight();
        }
        return (int) (adjustedWeight * (hallmarkBalance / Constants.ONE_EC) / Constants.MAX_BALANCE_EC);
    }

    @Override
    public boolean isBlacklisted() {
        return blacklistingTime > 0 || isOldVersion || Peers.ecknownBlacklistedPeers.contains(host)
                || (announcedAddress != null && Peers.ecknownBlacklistedPeers.contains(announcedAddress));
    }

    @Override
    public void blacklist(Exception cause) {
        if (cause instanceof EcNotCurrentlyValidExceptionEc || cause instanceof BlockOutOfOrderException
                || cause instanceof SQLException || cause.getCause() instanceof SQLException) {
            // don't blacklist peers just because a feature is not yet enabled, or because of database timeouts
            // prevents erroneous blacklisting during loading of blockchain from scratch
            return;
        }
        if (cause instanceof ParseException && PeerErrors.END_OF_FILE.equals(cause.toString())) {
            return;
        }
        if (!isBlacklisted()) {
            if (cause instanceof IOException || cause instanceof ParseException || cause instanceof IllegalArgumentException) {
                LoggerUtil.logDebug("Blacklisting " + host + " because of: " + cause.toString());
            } else {
                LoggerUtil.logError("Blacklisting " + host + " because of: " + cause.toString(), cause);
            }
        }
        blacklist(cause.toString() == null || Constants.HIDE_ERROR_DETAILS ? cause.getClass().getName() : cause.toString());
    }

    @Override
    public void blacklist(String cause) {
        blacklistingTime = new EcTime.EpochEcTime().getTime();
        blacklistingCause = cause;
        setPeerState(PeerState.NON_CONNECTED);
        lastInboundRequest = 0;
        Peers.notifyListeners(this, PeersEvent.BLACKLIST);
    }

    @Override
    public void unBlacklist() {
        if (blacklistingTime == 0) {
            return;
        }
        LoggerUtil.logDebug("Unblacklisting " + host);
        setPeerState(PeerState.NON_CONNECTED);
        blacklistingTime = 0;
        blacklistingCause = null;
        Peers.notifyListeners(this, PeersEvent.UNBLACKLIST);
    }

    @Override
    public void deactivate() {
        if (state == PeerState.CONNECTED) {
            setPeerState(PeerState.DISCONNECTED);
        } else {
            setPeerState(PeerState.NON_CONNECTED);
        }
        Peers.notifyListeners(this, PeersEvent.DEACTIVATE);
    }

    @Override
    public void remove() {
        peerWebSocket.close();
        Peers.removePeer(this);
        Peers.notifyListeners(this, PeersEvent.REMOVE);
    }

    @Override
    public int getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public boolean isInbound() {
        return lastInboundRequest != 0;
    }

    @Override
    public boolean isInboundWebSocket() {
        PeerWebSocket s;
        return ((s = inpeerWebSocket) != null && s.isOpen());
    }

    void setInboundWebSocket(PeerWebSocket inboundSocket) {
        this.inpeerWebSocket = inboundSocket;
    }

    @Override
    public boolean isOutboundWebSocket() {
        return peerWebSocket.isOpen();
    }

    @Override
    public String getBlacklistingCause() {
        return blacklistingCause == null ? "unknown" : blacklistingCause;
    }

    @Override
    public int getLastConnectAttempt() {
        return lastConnectAttempt;
    }

    @Override
    public JSONObject send(final JSONStreamAware request) {
        return send(request, Constants.EC_MAX_RESPONSE_SIZE);
    }

    @Override
    public JSONObject send(final JSONStreamAware request, int maxResponseSize) {
        JSONObject response = null;
        String log = null;
        boolean showLog = false;
        HttpURLConnection connection = null;
        int communicationLoggingMask = Peers.communicationLoggingMask;

        try {
            //
            // Create a new WebSocket session if we don't have one
            //
            if (useWebSocket && !peerWebSocket.isOpen()) {
                useWebSocket = peerWebSocket.startClient(URI.create("ws://" + host + ":" + getPeerPort() + "/ec"));
            }
            //
            // Send the request and process the response
            //
            if (useWebSocket) {
                //
                // Send the request using the WebSocket session
                //
                StringWriter wsWriter = new StringWriter(1000);
                request.writeJSONString(wsWriter);
                String wsRequest = wsWriter.toString();
                if (communicationLoggingMask != 0) {
                    log = "WebSocket " + host + ": " + wsRequest;
                }
                String wsResponse = peerWebSocket.doPost(wsRequest);
                updateUploadedVolume(wsRequest.length());
                if (maxResponseSize > 0) {
                    if ((communicationLoggingMask & Constants.EC_LOGGING_MASK_200_RESPONSES) != 0) {
                        log += " >>> " + wsResponse;
                        showLog = true;
                    }
                    if (wsResponse.length() > maxResponseSize) {
                        throw new EcIOException("Maximum size exceeded: " + wsResponse.length());
                    }
                    response = (JSONObject) JSONValue.parseWithException(wsResponse);
                    updateDownloadedVolume(wsResponse.length());
                }
            } else {
                //
                // Send the request using HTTP
                //
                URL url = new URL("http://" + host + ":" + getPeerPort() + "/ec");
                if (communicationLoggingMask != 0) {
                    log = "\"" + url.toString() + "\": " + JSON.toString(request);
                }
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(Peers.ecconnectTimeout);
                connection.setReadTimeout(Peers.ecreadTimeout);
                connection.setRequestProperty("Accept-Encoding", "gzip");
                connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                try (Writer writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"))) {
                    CalculatingOutputWriter cow = new CalculatingOutputWriter(writer);
                    request.writeJSONString(cow);
                    updateUploadedVolume(cow.getCount());
                }
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    if (maxResponseSize > 0) {
                        if ((communicationLoggingMask & Constants.EC_LOGGING_MASK_200_RESPONSES) != 0) {
                            CalculatingInputStream cis = new CalculatingInputStream(connection.getInputStream(), maxResponseSize);
                            InputStream responseStream = cis;
                            if ("gzip".equals(connection.getHeaderField("Content-Encoding"))) {
                                responseStream = new GZIPInputStream(cis);
                            }
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int numberOfBytes;
                            try (InputStream inputStream = responseStream) {
                                while ((numberOfBytes = inputStream.read(buffer, 0, buffer.length)) > 0) {
                                    byteArrayOutputStream.write(buffer, 0, numberOfBytes);
                                }
                            }
                            String responseValue = byteArrayOutputStream.toString("UTF-8");
                            if (responseValue.length() > 0 && responseStream instanceof GZIPInputStream) {
                                log += String.format("[length: %d, compression ratio: %.2f]",
                                        cis.getCount(), (double) cis.getCount() / (double) responseValue.length());
                            }
                            log += " >>> " + responseValue;
                            showLog = true;
                            response = (JSONObject) JSONValue.parseWithException(responseValue);
                            updateDownloadedVolume(responseValue.length());
                        } else {
                            InputStream responseStream = connection.getInputStream();
                            if ("gzip".equals(connection.getHeaderField("Content-Encoding"))) {
                                responseStream = new GZIPInputStream(responseStream);
                            }
                            try (Reader reader = new BufferedReader(new InputStreamReader(responseStream, "UTF-8"))) {
                                CalculatingInputReader cir = new CalculatingInputReader(reader, maxResponseSize);
                                response = (JSONObject) JSONValue.parseWithException(cir);
                                updateDownloadedVolume(cir.getCount());
                            }
                        }
                    }
                } else {
                    if ((communicationLoggingMask & Constants.EC_LOGGING_MASK_NON200_RESPONSES) != 0) {
                        log += " >>> Peer responded with HTTP " + connection.getResponseCode() + " code!";
                        showLog = true;
                    }
                    LoggerUtil.logDebug("Peer " + host + " responded with HTTP " + connection.getResponseCode());
                    deactivate();
                    connection.disconnect();
                }
            }
            //
            // Check for an error response
            //
            if (response != null && response.get("error") != null) {
                deactivate();
                if (PeerErrors.SEQUENCE_ERROR.equals(response.get("error")) && request != Peers.getMyPeerInfoRequest()) {
                    LoggerUtil.logDebug("Sequence error, reconnecting to " + host);
                    connect();
                } else {
                    LoggerUtil.logDebug("Peer " + host + " version " + version + " returned error: " +
                            response.toJSONString() + ", request was: " + JSON.toString(request) +
                            ", disconnecting");
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        } catch (EcIOException e) {
            blacklist(e);
            if (connection != null) {
                connection.disconnect();
            }
        } catch (RuntimeException | ParseException | IOException e) {
            if (!(e instanceof UnknownHostException || e instanceof SocketTimeoutException ||
                    e instanceof SocketException || PeerErrors.END_OF_FILE.equals(e.getMessage()))) {
                LoggerUtil.logDebug(String.format("Error sending request to peer %s: %s",
                        host, e.getMessage() != null ? e.getMessage() : e.toString()));
            }
            if ((communicationLoggingMask & Constants.EC_LOGGING_MASK_EXCEPTIONS) != 0) {
                log += " >>> " + e.toString();
                showLog = true;
            }
            deactivate();
            if (connection != null) {
                connection.disconnect();
            }
        }
        if (showLog) {
            LoggerUtil.logInfo(log + "\n");
        }

        return response;
    }

    @Override
    public int compareTo(Peer o) {
        if (getPeerWeight() > o.getPeerWeight()) {
            return -1;
        } else if (getPeerWeight() < o.getPeerWeight()) {
            return 1;
        }
        return getPeerHost().compareTo(o.getPeerHost());
    }

    @Override
    public boolean providesService(PeerService service) {
        boolean isProvided;
        synchronized (this) {
            isProvided = ((services & service.getCode()) != 0);
        }
        return isProvided;
    }

    @Override
    public boolean providesServices(long services) {
        boolean isProvided;
        synchronized (this) {
            isProvided = (services & this.services) == services;
        }
        return isProvided;
    }

    @Override
    public boolean isOpenAPI() {
        return providesService(PeerService.API) || providesService(PeerService.API_SSL);
    }

    @Override
    public boolean isApiConnectable() {
        return isOpenAPI() && state == PeerState.CONNECTED
                && !Peers.isOldVersion(version, Constants.EC_MIN_PROXY_VERSION)
                && !Peers.isNewVersion(version)
                && blockchainState == PeerBlockchainState.UP_TO_DATE;
    }

    @Override
    public StringBuilder getPeerApiUri() {
        StringBuilder uri = new StringBuilder();
        if (providesService(PeerService.API_SSL)) {
            uri.append("https://");
        } else {
            uri.append("http://");
        }
        uri.append(host).append(":");
        if (providesService(PeerService.API_SSL)) {
            uri.append(apiSSLPort);
        } else {
            uri.append(apiPort);
        }
        return uri;
    }

    @Override
    public String toString() {
        return "Peer{" +
                "state=" + state +
                ", announcedAddress='" + announcedAddress + '\'' +
                ", services=" + services +
                ", host='" + host + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    void connect() {
        lastConnectAttempt = new EcTime.EpochEcTime().getTime();
        try {
            if (!Peers.ignoreecPeerAnnouncedAddress && announcedAddress != null) {
                try {
                    URI uri = new URI("http://" + announcedAddress);
                    InetAddress inetAddress = InetAddress.getByName(uri.getHost());
                    if (!inetAddress.equals(InetAddress.getByName(host))) {
                        LoggerUtil.logDebug("Connect: announced address " + announcedAddress + " now points to " + inetAddress.getHostAddress() + ", replacing peer " + host);
                        Peers.removePeer(this);
                        PeerImpl newPeer = Peers.selectOrCreatePeer(inetAddress, announcedAddress, true);
                        if (newPeer != null) {
                            Peers.addPeer(newPeer);
                            newPeer.connect();
                        }
                        return;
                    }
                } catch (URISyntaxException | UnknownHostException e) {
                    blacklist(e);
                    return;
                }
            }
            JSONObject response = send(Peers.getMyPeerInfoRequest());
            if (response != null) {
                if (response.get("error") != null) {
                    setPeerState(PeerState.NON_CONNECTED);
                    return;
                }
                String servicesString = (String) response.get("services");
                long origServices = services;
                services = (servicesString != null ? Long.parseUnsignedLong(servicesString) : 0);
                setPeerApplication((String) response.get("application"));
                setApiPort(response.get("apiPort"));
                setApiSSLPort(response.get("apiSSLPort"));
                setDisabledAPIs(response.get("disabledAPIs"));
                setApiServerIdleTimeout(response.get("apiServerIdleTimeout"));
                setPeerBlockchainState(response.get("blockchainState"));
                lastUpdated = lastConnectAttempt;
                setPeerVersion((String) response.get("version"));
                setPeerPlatform((String) response.get("platform"));
                shareAddress = Boolean.TRUE.equals(response.get("shareAddress"));
                analyzeHallmark((String) response.get("qualityProof"));

                if (!Peers.ignoreecPeerAnnouncedAddress) {
                    String newAnnouncedAddress = Convert.emptyToNull((String) response.get("announcedAddress"));
                    if (newAnnouncedAddress != null) {
                        newAnnouncedAddress = Peers.addressWithPort(newAnnouncedAddress.toLowerCase());
                        if (newAnnouncedAddress != null) {
                            if (!verifyAnnouncedAddress(newAnnouncedAddress)) {
                                LoggerUtil.logDebug("Connect: new announced address for " + host + " not accepted");
                                if (!verifyAnnouncedAddress(announcedAddress)) {
                                    LoggerUtil.logDebug("Connect: old announced address for " + host + " no longer valid");
                                    Peers.setAnnouncedAddress(this, host);
                                }
                                setPeerState(PeerState.NON_CONNECTED);
                                return;
                            }
                            if (!newAnnouncedAddress.equals(announcedAddress)) {
                                LoggerUtil.logDebug("Connect: peer " + host + " has new announced address " + newAnnouncedAddress + ", old is " + announcedAddress);
                                int oldPort = getPeerPort();
                                Peers.setAnnouncedAddress(this, newAnnouncedAddress);
                                if (getPeerPort() != oldPort) {
                                    // force checking connectivity to new announced port
                                    setPeerState(PeerState.NON_CONNECTED);
                                    return;
                                }
                            }
                        }
                    } else {
                        Peers.setAnnouncedAddress(this, host);
                    }
                }

                if (announcedAddress == null) {
                    if (qualityProof == null || qualityProof.getPort() == Peers.getDefaultPeerPort()) {
                        Peers.setAnnouncedAddress(this, host);
                        LoggerUtil.logDebug("Connected to peer without announced address, setting to " + host);
                    } else {
                        setPeerState(PeerState.NON_CONNECTED);
                        return;
                    }
                }

                if (!isOldVersion) {
                    setPeerState(PeerState.CONNECTED);
                    if (services != origServices) {
                        Peers.notifyListeners(this, PeersEvent.CHANGED_SERVICES);
                    }
                } else if (!isBlacklisted()) {
                    blacklist("Old version: " + version);
                }
            } else {
                //LoggerUtil.logDebug("Failed to connect to peer " + peerAddress);
                setPeerState(PeerState.NON_CONNECTED);
            }
        } catch (RuntimeException e) {
            blacklist(e);
        }
    }

    boolean verifyAnnouncedAddress(String newAnnouncedAddress) {
        if (newAnnouncedAddress == null) {
            return true;
        }
        try {
            URI uri = new URI("http://" + newAnnouncedAddress);
            int announcedPort = uri.getPort() == -1 ? Peers.getDefaultPeerPort() : uri.getPort();
            if (qualityProof != null && announcedPort != qualityProof.getPort()) {
                LoggerUtil.logDebug("Announced port " + announcedPort + " does not match qualityProof " + qualityProof.getPort() + ", ignoring qualityProof for " + host);
                unsetHallmark();
            }
            InetAddress address = InetAddress.getByName(host);
            for (InetAddress inetAddress : InetAddress.getAllByName(uri.getHost())) {
                if (inetAddress.equals(address)) {
                    return true;
                }
            }
            LoggerUtil.logDebug("Announced address " + newAnnouncedAddress + " does not resolve to " + host);
        } catch (UnknownHostException | URISyntaxException e) {
            LoggerUtil.logDebug(e.toString());
            blacklist(e);
        }
        return false;
    }

    boolean analyzeHallmark(final String hallmarkString) {
        if (Constants.IS_LIGHT_CLIENT) {
            return true;
        }

        if (hallmarkString == null && this.qualityProof == null) {
            return true;
        }

        if (this.qualityProof != null && this.qualityProof.getHallmarkString().equals(hallmarkString)) {
            return true;
        }

        if (hallmarkString == null) {
            unsetHallmark();
            return true;
        }

        try {

            QualityProof qualityProof = QualityProof.parseHallmark(hallmarkString);
            if (!qualityProof.isValid()) {
                LoggerUtil.logDebug("Invalid qualityProof " + hallmarkString + " for " + host);
                unsetHallmark();
                return false;
            }
            if (!qualityProof.getHost().equals(host)) {
                InetAddress hostAddress = InetAddress.getByName(host);
                boolean validHost = false;
                for (InetAddress nextHallmark : InetAddress.getAllByName(qualityProof.getHost())) {
                    if (hostAddress.equals(nextHallmark)) {
                        validHost = true;
                        break;
                    }
                }
                if (!validHost) {
                    LoggerUtil.logDebug("QualityProof host " + qualityProof.getHost() + " doesn't match " + host);
                    unsetHallmark();
                    return false;
                }
            }
            setQualityProof(qualityProof);
            long accountId = Account.getId(qualityProof.getPublicKey());
            List<PeerImpl> groupedPeers = new ArrayList<>();
            int mostRecentDate = 0;
            long totalWeight = 0;
            for (PeerImpl peer : Peers.allPeers) {
                if (peer.qualityProof == null) {
                    continue;
                }
                if (accountId == peer.qualityProof.getAccountId()) {
                    groupedPeers.add(peer);
                    if (peer.qualityProof.getDate() > mostRecentDate) {
                        mostRecentDate = peer.qualityProof.getDate();
                        totalWeight = peer.getHallmarkWeight(mostRecentDate);
                    } else {
                        totalWeight += peer.getHallmarkWeight(mostRecentDate);
                    }
                }
            }

            for (PeerImpl peer : groupedPeers) {
                peer.adjustedWeight = Constants.MAX_BALANCE_EC * peer.getHallmarkWeight(mostRecentDate) / totalWeight;
                Peers.notifyListeners(peer, PeersEvent.WEIGHT);
            }

            return true;

        } catch (UnknownHostException ignore) {
        } catch (RuntimeException e) {
            LoggerUtil.logError("Failed to analyze qualityProof for peer " + host + ", " + e.toString(), e);
        }
        unsetHallmark();
        return false;

    }

    private int getHallmarkWeight(int date) {
        if (qualityProof == null || !qualityProof.isValid() || qualityProof.getDate() != date) {
            return 0;
        }
        return qualityProof.getWeight();
    }

    private void unsetHallmark() {
        delService(PeerService.HALLMARK, false);
        this.qualityProof = null;
    }

    private void putService(PeerService service, boolean doNotify) {
        boolean notifyListeners;
        synchronized (this) {
            notifyListeners = ((services & service.getCode()) == 0);
            services |= service.getCode();
        }
        if (notifyListeners && doNotify) {
            Peers.notifyListeners(this, PeersEvent.CHANGED_SERVICES);
        }
    }

    private void delService(PeerService service, boolean doNotify) {
        boolean notifyListeners;
        synchronized (this) {
            notifyListeners = ((services & service.getCode()) != 0);
            services &= (~service.getCode());
        }
        if (notifyListeners && doNotify) {
            Peers.notifyListeners(this, PeersEvent.CHANGED_SERVICES);
        }
    }

    long getServices() {
        synchronized (this) {
            return services;
        }
    }

    void setServices(long services) {
        synchronized (this) {
            this.services = services;
        }
    }

    int getLastInboundRequest() {
        return lastInboundRequest;
    }

    void setLastInboundRequest(int now) {
        lastInboundRequest = now;
    }

    void setLastUpdated(int lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    void updateBlacklistedStatus(int curTime) {
        if (blacklistingTime > 0 && blacklistingTime + Peers.ecblacklistingPeriod <= curTime) {
            unBlacklist();
        }
        if (isOldVersion && lastUpdated < curTime - 3600) {
            isOldVersion = false;
        }
    }

    private void setQualityProof(QualityProof qualityProof) {
        this.qualityProof = qualityProof;
        putService(PeerService.HALLMARK, false);
    }

    void setAnnouncedAddress(String announcedAddress) {
        if (announcedAddress != null && announcedAddress.length() > Constants.EC_MAX_ANNOUNCED_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("Announced address too long: " + announcedAddress.length());
        }
        this.announcedAddress = announcedAddress;
        if (announcedAddress != null) {
            try {
                this.port = new URI("http://" + announcedAddress).getPort();
            } catch (URISyntaxException e) {
                this.port = -1;
            }
        } else {
            this.port = -1;
        }
    }

    void setPeerShareAddress(boolean shareAddress) {
        this.shareAddress = shareAddress;
    }

    void setPeerBlockchainState(Object blockchainStateObj) {
        if (blockchainStateObj instanceof Integer) {
            int blockchainStateInt = (int) blockchainStateObj;
            if (blockchainStateInt >= 0 && blockchainStateInt < PeerBlockchainState.values().length) {
                this.blockchainState = PeerBlockchainState.values()[blockchainStateInt];
            }
        }
    }

    void setApiServerIdleTimeout(Object apiServerIdleTimeout) {
        if (apiServerIdleTimeout instanceof Integer) {
            this.apiServerIdleTimeout = (int) apiServerIdleTimeout;
        }
    }

    void setDisabledAPIs(Object apiSetBase64) {
        if (apiSetBase64 instanceof String) {
            disabledAPIs = APIEnum.base64ECStringToEnumSet((String) apiSetBase64);
        }
    }

    void setApiSSLPort(Object apiSSLPortValue) {
        if (apiSSLPortValue != null) {
            try {
                apiSSLPort = ((Long) apiSSLPortValue).intValue();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid peer apiSSLPort " + apiSSLPortValue);
            }
        }
    }

    void setApiPort(Object apiPortValue) {
        if (apiPortValue != null) {
            try {
                apiPort = ((Long) apiPortValue).intValue();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid peer apiPort " + apiPortValue);
            }
        }
    }

    void setPeerPlatform(String platform) {
        if (platform != null && platform.length() > Constants.EC_MAX_PLATFORM_LENGTH) {
            throw new IllegalArgumentException("Invalid platform length: " + platform.length());
        }
        this.platform = platform;
    }

    void setPeerApplication(String application) {
        if (application == null || application.length() > Constants.EC_MAX_APPLICATION_LENGTH) {
            throw new IllegalArgumentException("Invalid application");
        }
        this.application = application;
    }

    void setPeerVersion(String version) {
        if (version != null && version.length() > Constants.EC_MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("Invalid version length: " + version.length());
        }
        boolean versionChanged = version == null || !version.equals(this.version);
        this.version = version;
        isOldVersion = false;
        if (Constants.EC_APPLICATION.equals(application)) {
            isOldVersion = Peers.isOldVersion(version, Constants.EC_MIN_VERSION);
            if (isOldVersion) {
                if (versionChanged) {
                    LoggerUtil.logDebug(String.format("Blacklisting %s version %s", host, version));
                }
                blacklistingCause = "Old version: " + version;
                lastInboundRequest = 0;
                setPeerState(PeerState.NON_CONNECTED);
                Peers.notifyListeners(this, PeersEvent.BLACKLIST);
            }
        }
    }

    void updateUploadedVolume(long volume) {
        synchronized (this) {
            uploadedVolume += volume;
        }
        Peers.notifyListeners(this, PeersEvent.UPLOADED_VOLUME);
    }

    void updateDownloadedVolume(long volume) {
        synchronized (this) {
            downloadedVolume += volume;
        }
        Peers.notifyListeners(this, PeersEvent.DOWNLOADED_VOLUME);
    }

    void setPeerState(PeerState state) {
        if (state != PeerState.CONNECTED) {
            peerWebSocket.close();
        }
        if (this.state == state) {
            return;
        }
        if (this.state == PeerState.NON_CONNECTED) {
            this.state = state;
            Peers.notifyListeners(this, PeersEvent.ADDED_ACTIVE_PEER);
        } else if (state != PeerState.NON_CONNECTED) {
            this.state = state;
            Peers.notifyListeners(this, PeersEvent.CHANGED_ACTIVE_PEER);
        } else {
            this.state = state;
        }
    }
}
