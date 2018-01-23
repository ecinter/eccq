package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.peer.PeerState;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DumpPeers extends APIRequestHandler {

    static final DumpPeers instance = new DumpPeers();

    private DumpPeers() {
        super(new APITag[]{APITag.DEBUG}, "version", "weight", "connect", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String version = Convert.nullToEmpty(req.getParameter("version"));
        int weight = ParameterParser.getInt(req, "weight", 0, (int) Constants.MAX_BALANCE_EC, false);
        boolean connect = "true".equalsIgnoreCase(req.getParameter("connect")) && API.checkPassword(req);
        if (connect) {
            List<Callable<Object>> connects = new ArrayList<>();
            Peers.getAllPeers().forEach(peer -> connects.add(() -> {
                Peers.connectPeer(peer);
                return null;
            }));
            ExecutorService service = Executors.newFixedThreadPool(10);
            try {
                service.invokeAll(connects);
            } catch (InterruptedException e) {
                LoggerUtil.logError(e.toString(), e);
            }
        }
        Set<String> addresses = new HashSet<>();
        Peers.getAllPeers().forEach(peer -> {
            if (peer.getState() == PeerState.CONNECTED
                    && peer.shareAddress()
                    && !peer.isBlacklisted()
                    && peer.getPeerVersion() != null && peer.getPeerVersion().startsWith(version)
                    && (weight == 0 || peer.getPeerWeight() > weight)) {
                addresses.add(peer.getAnnouncedAddress());
            }
        });
        StringBuilder buf = new StringBuilder();
        for (String address : addresses) {
            buf.append(address).append("; ");
        }
        JSONObject response = new JSONObject();
        response.put("peers", buf.toString());
        response.put("count", addresses.size());
        return response;
    }

    @Override
    protected final boolean requirePost() {
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
