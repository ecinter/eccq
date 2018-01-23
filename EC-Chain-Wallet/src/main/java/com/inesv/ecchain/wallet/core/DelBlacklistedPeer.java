package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.kernel.peer.Peer;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;

import static com.inesv.ecchain.wallet.core.JSONResponse.EC_LOCAL_USERS_ONLY;


public final class DelBlacklistedPeer extends UserRequestHandler {

    static final DelBlacklistedPeer instance = new DelBlacklistedPeer();

    private DelBlacklistedPeer() {
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws IOException {
        if (Users.allowedUsersHosts == null && !InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
            return EC_LOCAL_USERS_ONLY;
        } else {
            int index = Integer.parseInt(req.getParameter("peer"));
            Peer peer = Users.getEcPeer(index);
            if (peer != null && peer.isBlacklisted()) {
                peer.unBlacklist();
            }
        }
        return null;
    }
}
