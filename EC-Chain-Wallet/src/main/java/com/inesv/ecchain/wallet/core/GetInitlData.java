package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.PeerState;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;

public final class GetInitlData extends UserRequestHandler {

    static final GetInitlData instance = new GetInitlData();

    private GetInitlData() {
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws IOException {

        JSONArray unconfirmedTransactions = new JSONArray();
        JSONArray activePeers = new JSONArray(), knownPeers = new JSONArray(), blacklistedPeers = new JSONArray();
        JSONArray recentBlocks = new JSONArray();

        try (H2Iterator<? extends Transaction> transactions = TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions()) {
            while (transactions.hasNext()) {
                Transaction transaction = transactions.next();

                JSONObject unconfirmedTransaction = new JSONObject();
                unconfirmedTransaction.put("index", Users.getIndex(transaction));
                unconfirmedTransaction.put("timestamp", transaction.getTimestamp());
                unconfirmedTransaction.put("deadline", transaction.getDeadline());
                unconfirmedTransaction.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
                unconfirmedTransaction.put("amountNQT", transaction.getAmountNQT());
                unconfirmedTransaction.put("feeNQT", transaction.getFeeNQT());
                unconfirmedTransaction.put("sender", Long.toUnsignedString(transaction.getSenderId()));
                unconfirmedTransaction.put("id", transaction.getStringId());

                unconfirmedTransactions.add(unconfirmedTransaction);
            }
        }

        for (Peer peer : Peers.getAllPeers()) {

            if (peer.isBlacklisted()) {

                JSONObject blacklistedPeer = new JSONObject();
                blacklistedPeer.put("index", Users.getIndex(peer));
                blacklistedPeer.put("address", peer.getPeerHost());
                blacklistedPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                blacklistedPeer.put("software", peer.getSoftware());
                blacklistedPeers.add(blacklistedPeer);

            } else if (peer.getState() == PeerState.NON_CONNECTED) {

                JSONObject knownPeer = new JSONObject();
                knownPeer.put("index", Users.getIndex(peer));
                knownPeer.put("address", peer.getPeerHost());
                knownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                knownPeer.put("software", peer.getSoftware());
                knownPeers.add(knownPeer);

            } else {

                JSONObject activePeer = new JSONObject();
                activePeer.put("index", Users.getIndex(peer));
                if (peer.getState() == PeerState.DISCONNECTED) {
                    activePeer.put("disconnected", true);
                }
                activePeer.put("address", peer.getPeerHost());
                activePeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                activePeer.put("weight", peer.getPeerWeight());
                activePeer.put("downloaded", peer.getDownloadedVolume());
                activePeer.put("uploaded", peer.getUploadedVolume());
                activePeer.put("software", peer.getSoftware());
                activePeers.add(activePeer);
            }
        }

        try (H2Iterator<? extends EcBlock> lastBlocks = EcBlockchainImpl.getInstance().getBlocks(0, 59)) {
            for (EcBlock ecBlock : lastBlocks) {
                JSONObject recentBlock = new JSONObject();
                recentBlock.put("index", Users.getIndex(ecBlock));
                recentBlock.put("timestamp", ecBlock.getTimestamp());
                recentBlock.put("numberOfTransactions", ecBlock.getTransactions().size());
                recentBlock.put("totalAmountNQT", ecBlock.getTotalAmountNQT());
                recentBlock.put("totalFeeNQT", ecBlock.getTotalFeeNQT());
                recentBlock.put("payloadLength", ecBlock.getPayloadLength());
                recentBlock.put("generator", Long.toUnsignedString(ecBlock.getFoundryId()));
                recentBlock.put("height", ecBlock.getHeight());
                recentBlock.put("version", ecBlock.getECVersion());
                recentBlock.put("ecBlock", ecBlock.getStringECId());
                recentBlock.put("baseTarget", BigInteger.valueOf(ecBlock.getBaseTarget()).multiply(BigInteger.valueOf(100000))
                        .divide(BigInteger.valueOf(Constants.EC_INITIAL_BASE_TARGET)));

                recentBlocks.add(recentBlock);
            }
        }

        JSONObject response = new JSONObject();
        response.put("response", "processInitialData");
        response.put("version", Constants.EC_VERSION);
        if (unconfirmedTransactions.size() > 0) {
            response.put("unconfirmedTransactions", unconfirmedTransactions);
        }
        if (activePeers.size() > 0) {
            response.put("activePeers", activePeers);
        }
        if (knownPeers.size() > 0) {
            response.put("knownPeers", knownPeers);
        }
        if (blacklistedPeers.size() > 0) {
            response.put("blacklistedPeers", blacklistedPeers);
        }
        if (recentBlocks.size() > 0) {
            response.put("recentBlocks", recentBlocks);
        }

        return response;
    }
}
