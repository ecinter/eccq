package com.inesv.ecchain.kernel.peer;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

abstract class PeerRequestHandler {
    abstract JSONStreamAware disposeRequest(JSONObject request, Peer peer);

    abstract boolean rejectRequest();
}
