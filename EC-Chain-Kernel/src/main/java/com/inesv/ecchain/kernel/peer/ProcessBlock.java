package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class ProcessBlock extends PeerRequestHandler {

    static final ProcessBlock instance = new ProcessBlock();

    private ProcessBlock() {
    }

    @Override
    JSONStreamAware disposeRequest(final JSONObject request, final Peer peer) {
        String previousBlockId = (String) request.get("previousBlock");
        EcBlock lastEcBlock = EcBlockchainImpl.getInstance().getLastECBlock();
        if (lastEcBlock.getStringECId().equals(previousBlockId) ||
                (Convert.parseUnsignedLong(previousBlockId) == lastEcBlock.getPreviousBlockId()
                        && lastEcBlock.getTimestamp() > Convert.parseLong(request.get("timestamp")))) {
            Peers.peersService.submit(() -> {
                try {
                    EcBlockchainProcessorImpl.getInstance().processPeerBlock(request);
                } catch (EcException | RuntimeException e) {
                    if (peer != null) {
                        peer.blacklist(e);
                    }
                }
            });
        }
        return JSON.EMPTY_JSON;
    }

    @Override
    boolean rejectRequest() {
        return true;
    }

}
