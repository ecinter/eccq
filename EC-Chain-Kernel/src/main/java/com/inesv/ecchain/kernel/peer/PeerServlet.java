package com.inesv.ecchain.kernel.peer;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessor;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.eclipse.jetty.websocket.servlet.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PeerServlet extends WebSocketServlet {

    static final JSONStreamAware UNSUPPORTED_REQUEST_TYPE;
    private static final Map<String, PeerRequestHandler> peerRequestHandlers;
    private static final JSONStreamAware UNSUPPORTED_PROTOCOL;
    private static final JSONStreamAware UNKNOWN_PEER;
    private static final JSONStreamAware SEQUENCE_ERROR;
    private static final JSONStreamAware MAX_INBOUND_CONNECTIONS;
    private static final JSONStreamAware DOWNLOADING;
    private static final JSONStreamAware LIGHT_CLIENT;
    private static final EcBlockchainProcessor EC_BLOCKCHAIN_PROCESSOR = EcBlockchainProcessorImpl.getInstance();

    static {
        Map<String, PeerRequestHandler> map = new HashMap<>();
        map.put("addPeers", AddEcPeers.instance);
        map.put("getCumulativeDifficulty", GetCumulativeDifficulty.instance);
        map.put("getInfo", GetMessage.instance);
        map.put("getMilestoneBlockIds", GetLandmarkBlockIds.instance);
        map.put("getNextBlockIds", GetNextBlockIds.instance);
        map.put("getNextBlocks", GetNextBlocks.instance);
        map.put("getPeers", GetEcPeers.instance);
        map.put("getTransactions", GetEcTransactions.instance);
        map.put("getUnconfirmedTransactions", GetUnconfirmedTransactions.instance);
        map.put("processBlock", ProcessBlock.instance);
        map.put("processTransactions", ProcessTransactions.instance);
        peerRequestHandlers = Collections.unmodifiableMap(map);
    }

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.UNSUPPORTED_REQUEST_TYPE);
        UNSUPPORTED_REQUEST_TYPE = JSON.prepare(response);
    }

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.UNSUPPORTED_PROTOCOL);
        UNSUPPORTED_PROTOCOL = JSON.prepare(response);
    }

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.UNKNOWN_PEER);
        UNKNOWN_PEER = JSON.prepare(response);
    }

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.SEQUENCE_ERROR);
        SEQUENCE_ERROR = JSON.prepare(response);
    }

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.MAX_INBOUND_CONNECTIONS);
        MAX_INBOUND_CONNECTIONS = JSON.prepare(response);
    }

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.DOWNLOADING);
        DOWNLOADING = JSON.prepare(response);
    }

    static {
        JSONObject response = new JSONObject();
        response.put("error", PeerErrors.LIGHT_CLIENT);
        LIGHT_CLIENT = JSON.prepare(response);
    }

    static JSONStreamAware error(Exception e) {
        JSONObject response = new JSONObject();
        response.put("error", Constants.HIDE_ERROR_DETAILS ? e.getClass().getName() : e.toString());
        return response;
    }

    /**
     * Configure the WebSocket factory
     *
     * @param factory WebSocket factory
     */
    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(Peers.webecSocketIdleTimeout);
        factory.getPolicy().setMaxBinaryMessageSize(Constants.EC_MAX_MESSAGE_SIZE);
        factory.setCreator(new PeerSocketCreator());
    }

    /**
     * Process HTTP POST request
     *
     * @param req  HTTP request
     * @param resp HTTP response
     * @throws ServletException Servlet processing error
     * @throws IOException      I/O error
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONStreamAware jsonResponse;
        //
        // Process the peer request
        //
        PeerImpl peer = Peers.selectOrCreatePeer(req.getRemoteAddr());
        if (peer == null) {
            jsonResponse = UNKNOWN_PEER;
        } else {
            jsonResponse = process(peer, req.getReader());
        }
        //
        // Return the response
        //
        resp.setContentType("text/plain; charset=UTF-8");
        try (CalculatingOutputWriter writer = new CalculatingOutputWriter(resp.getWriter())) {
            JSON.writeECJSONString(jsonResponse, writer);
            if (peer != null) {
                peer.updateUploadedVolume(writer.getCount());
            }
        } catch (RuntimeException | IOException e) {
            if (peer != null) {
                if ((Peers.communicationLoggingMask & Constants.EC_LOGGING_MASK_EXCEPTIONS) != 0) {
                    if (e instanceof RuntimeException) {
                        LoggerUtil.logError("Error sending response to peer " + peer.getPeerHost(), e);
                    } else {
                        LoggerUtil.logDebug(String.format("Error sending response to peer %s: %s",
                                peer.getPeerHost(), e.getMessage() != null ? e.getMessage() : e.toString()));
                    }
                }
                peer.blacklist(e);
            }
            throw e;
        }
    }

    /**
     * Process WebSocket POST request
     *
     * @param webSocket WebSocket for the connection
     * @param requestId Request identifier
     * @param request   Request message
     */
    void doPost(PeerWebSocket webSocket, long requestId, String request) {
        JSONStreamAware jsonResponse;
        //
        // Process the peer request
        //
        InetSocketAddress socketAddress = webSocket.getRemoteAddress();
        if (socketAddress == null) {
            return;
        }
        String remoteAddress = socketAddress.getHostString();
        PeerImpl peer = Peers.selectOrCreatePeer(remoteAddress);
        if (peer == null) {
            jsonResponse = UNKNOWN_PEER;
        } else {
            peer.setInboundWebSocket(webSocket);
            jsonResponse = process(peer, new StringReader(request));
        }
        //
        // Return the response
        //
        try {
            StringWriter writer = new StringWriter(1000);
            JSON.writeECJSONString(jsonResponse, writer);
            String response = writer.toString();
            webSocket.sendResponse(requestId, response);
            if (peer != null) {
                peer.updateUploadedVolume(response.length());
            }
        } catch (RuntimeException | IOException e) {
            if (peer != null) {
                if ((Peers.communicationLoggingMask & Constants.EC_LOGGING_MASK_EXCEPTIONS) != 0) {
                    if (e instanceof RuntimeException) {
                        LoggerUtil.logError("Error sending response to peer " + peer.getPeerHost(), e);
                    } else {
                        LoggerUtil.logDebug(String.format("Error sending response to peer %s: %s",
                                peer.getPeerHost(), e.getMessage() != null ? e.getMessage() : e.toString()));
                    }
                }
                peer.blacklist(e);
            }
        }
    }

    /**
     * Process the peer request
     *
     * @param peer        Peer
     * @param inputReader Input reader
     * @return JSON response
     */
    private JSONStreamAware process(PeerImpl peer, Reader inputReader) {
        //
        // Check for blacklisted peer
        //
        if (peer.isBlacklisted()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("error", PeerErrors.BLACKLISTED);
            jsonObject.put("cause", peer.getBlacklistingCause());
            return jsonObject;
        }
        Peers.addPeer(peer);
        //
        // Process the request
        //
        try (CalculatingInputReader cr = new CalculatingInputReader(inputReader, Constants.EC_MAX_REQUEST_SIZE)) {
            JSONObject request = (JSONObject) JSONValue.parseWithException(cr);
            peer.updateDownloadedVolume(cr.getCount());
            if (request.get("protocol") == null || ((Number) request.get("protocol")).intValue() != 1) {
                LoggerUtil.logDebug("Unsupported protocol " + request.get("protocol"));
                return UNSUPPORTED_PROTOCOL;
            }
            PeerRequestHandler peerRequestHandler = peerRequestHandlers.get((String) request.get("requestType"));
            if (peerRequestHandler == null) {
                return UNSUPPORTED_REQUEST_TYPE;
            }
            if (peer.getState() == PeerState.DISCONNECTED) {
                peer.setPeerState(PeerState.CONNECTED);
            }
            if (peer.getPeerVersion() == null && !"getInfo".equals(request.get("requestType"))) {
                return SEQUENCE_ERROR;
            }
            if (!peer.isInbound()) {
                if (Peers.hasTooManyInboundPeers()) {
                    return MAX_INBOUND_CONNECTIONS;
                }
                Peers.notifyListeners(peer, PeersEvent.ADD_INBOUND);
            }
            peer.setLastInboundRequest(new EcTime.EpochEcTime().getTime());
            if (peerRequestHandler.rejectRequest()) {
                if (EC_BLOCKCHAIN_PROCESSOR.isDownloading()) {
                    return DOWNLOADING;
                }
                if (Constants.IS_LIGHT_CLIENT) {
                    return LIGHT_CLIENT;
                }
            }
            return peerRequestHandler.disposeRequest(request, peer);
        } catch (RuntimeException | ParseException | IOException e) {
            LoggerUtil.logDebug("Error processing POST request: " + e.toString());
            peer.blacklist(e);
            return error(e);
        }
    }

    /**
     * WebSocket creator for peer connections
     */
    private class PeerSocketCreator implements WebSocketCreator {
        /**
         * Create a peer WebSocket
         *
         * @param req  WebSocket upgrade request
         * @param resp WebSocket upgrade response
         * @return WebSocket
         */
        @Override
        public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
            return Peers.ecuseWebSockets ? new PeerWebSocket(PeerServlet.this) : null;
        }
    }
}
