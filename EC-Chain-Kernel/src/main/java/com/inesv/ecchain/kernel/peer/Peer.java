package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.kernel.http.APIEnum;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.Set;

public interface Peer extends Comparable<Peer> {

    boolean providesService(PeerService service);

    boolean providesServices(long services);

    String getPeerHost();

    int getPeerPort();

    String getAnnouncedAddress();

    PeerState getState();

    String getPeerVersion();

    String getApplication();

    String getPlatform();

    String getSoftware();

    int getApiPort();

    int getApiSSLPort();

    Set<APIEnum> getDisabledAPIs();

    int getApiServerIdleTimeout();

    PeerBlockchainState getBlockchainState();

    QualityProof getQualityProof();

    int getPeerWeight();

    boolean shareAddress();

    boolean isBlacklisted();

    void blacklist(Exception cause);

    void blacklist(String cause);

    void unBlacklist();

    void deactivate();

    void remove();

    long getDownloadedVolume();

    long getUploadedVolume();

    int getLastUpdated();

    int getLastConnectAttempt();

    boolean isInbound();

    boolean isInboundWebSocket();

    boolean isOutboundWebSocket();

    boolean isOpenAPI();

    boolean isApiConnectable();

    StringBuilder getPeerApiUri();

    String getBlacklistingCause();

    JSONObject send(JSONStreamAware request);

    JSONObject send(JSONStreamAware request, int maxResponseSize);

}
