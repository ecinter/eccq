package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class ProcessTransactions extends PeerRequestHandler {

    static final ProcessTransactions instance = new ProcessTransactions();

    private ProcessTransactions() {
    }


    @Override
    JSONStreamAware disposeRequest(JSONObject request, Peer peer) {

        try {
            TransactionProcessorImpl.getInstance().processPeerTransactions(request);
            return JSON.EMPTY_JSON;
        } catch (RuntimeException | EcValidationException e) {
            //LoggerUtil.logDebug("Failed to parse peer transactions: " + request.toECJSONString());
            peer.blacklist(e);
            return PeerServlet.error(e);
        }

    }

    @Override
    boolean rejectRequest() {
        return true;
    }

}
