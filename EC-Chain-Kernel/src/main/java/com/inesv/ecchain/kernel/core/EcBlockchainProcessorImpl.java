package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.kernel.H2.DerivedH2Table;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.FilteringIterator;
import com.inesv.ecchain.kernel.H2.FullTextTrigger;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.PeerService;
import com.inesv.ecchain.kernel.peer.PeerState;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
public final class EcBlockchainProcessorImpl implements EcBlockchainProcessor {
    private static final EcBlockchainProcessorImpl instance = new EcBlockchainProcessorImpl();
    private static final Comparator<Transaction> FINISHING_TRANSACTIONS_COMPARATOR = Comparator
            .comparingInt(Transaction::getTransactionHeight)
            .thenComparingInt(Transaction::getTransactionIndex)
            .thenComparingLong(Transaction::getTransactionId);
    private static final Comparator<UnconfirmedTransaction> TRANSACTION_ARRIVAL_COMPARATOR = Comparator
            .comparingLong(UnconfirmedTransaction::getArrivalTimestamp)
            .thenComparingInt(UnconfirmedTransaction::getTransactionHeight)
            .thenComparingLong(UnconfirmedTransaction::getTransactionId);
    private final EcBlockchainImpl blockchain = EcBlockchainImpl.getInstance();
    private final ExecutorService networkService = Executors.newCachedThreadPool();
    private final List<DerivedH2Table> derivedTables = new CopyOnWriteArrayList<>();
    private final Set<Long> prunableTransactions = new HashSet<>();
    private final ListenerManager<EcBlock, EcBlockchainProcessorEvent> blockListenerManager = new ListenerManager<>();
    private final Listener<EcBlock> checksumListener = block -> {

    };
    private int initialScanHeight;
    private volatile int lastTrimHeight;
    private volatile int lastRestoreTime = 0;
    private volatile Peer lastBlockchainFeeder;
    private volatile int lastBlockchainFeederHeight;
    private volatile boolean getMoreBlocks = true;
    private volatile boolean isTrimming;
    private volatile boolean isScanning;
    private volatile boolean isDownloading;
    private volatile boolean isProcessingBlock;
    private volatile boolean isRestoring;
    private final Runnable getMoreBlocksThread = new Runnable() {

        private final JSONStreamAware getCumulativeDifficultyRequest;
        private boolean peerHasMore;
        private List<Peer> connectedPublicPeers;
        private List<Long> chainBlockIds;
        private long totalTime = 1;
        private int totalBlocks;

        {
            JSONObject request = new JSONObject();
            request.put("requestType", "getCumulativeDifficulty");
            getCumulativeDifficultyRequest = JSON.prepareRequest(request);
        }

        @Override
        public void run() {
            try {
                //
                // Download blocks until we are up-to-date
                //
                while (true) {
                    if (!getMoreBlocks) {
                        return;
                    }
                    int chainHeight = blockchain.getHeight();
                    downloadPeer();
                    if (blockchain.getHeight() == chainHeight) {
                        if (isDownloading && !Constants.SIMULATE_ENDLESS_DOWNLOAD) {
                            LoggerUtil.logInfo("Finished EC_BLOCKCHAIN download");
                            isDownloading = false;
                        }
                        break;
                    }
                }
                //
                // Restore prunable data
                //
                int now = new EcTime.EpochEcTime().getTime();
                if (!isRestoring && !prunableTransactions.isEmpty() && now - lastRestoreTime > 60 * 60) {
                    isRestoring = true;
                    lastRestoreTime = now;
                    networkService.submit(new RestorePrunableDataTask());
                }
            } catch (InterruptedException e) {
                LoggerUtil.logDebug("EcBlockchain download thread interrupted");
            } catch (Throwable t) {
                LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString(), t);
                System.exit(1);
            }
        }

        private void downloadPeer() throws InterruptedException {
            try {
                long startTime = System.currentTimeMillis();
                int numberOfForkConfirmations = blockchain.getHeight() > Constants.EC_LAST_CHECKSUM_BLOCK ?
                        Constants.DEFAULT_NUMBER_OF_FORK_CONFIRMATIONS : Math.min(1, Constants.DEFAULT_NUMBER_OF_FORK_CONFIRMATIONS);
                connectedPublicPeers = Peers.getPublicPeers(PeerState.CONNECTED, true);
                if (connectedPublicPeers.size() <= numberOfForkConfirmations) {
                    return;
                }
                peerHasMore = true;
                final Peer peer = Peers.getWeightedPeer(connectedPublicPeers);
                if (peer == null) {
                    return;
                }
                JSONObject response = peer.send(getCumulativeDifficultyRequest);
                if (response == null) {
                    return;
                }
                BigInteger curCumulativeDifficulty = blockchain.getLastECBlock().getCumulativeDifficulty();
                String peerCumulativeDifficulty = (String) response.get("cumulativeDifficulty");
                if (peerCumulativeDifficulty == null) {
                    return;
                }
                BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
                if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                    return;
                }
                if (response.get("blockchainHeight") != null) {
                    lastBlockchainFeeder = peer;
                    lastBlockchainFeederHeight = ((Long) response.get("blockchainHeight")).intValue();
                }
                if (betterCumulativeDifficulty.equals(curCumulativeDifficulty)) {
                    return;
                }

                long commonMilestoneBlockId = Genesis.EC_GENESIS_BLOCK_ID;

                if (blockchain.getLastECBlock().getECId() != Genesis.EC_GENESIS_BLOCK_ID) {
                    commonMilestoneBlockId = getCommonMilestoneBlockId(peer);
                }
                if (commonMilestoneBlockId == 0 || !peerHasMore) {
                    return;
                }

                chainBlockIds = getBlockIdsAfterCommon(peer, commonMilestoneBlockId, false);
                if (chainBlockIds.size() < 2 || !peerHasMore) {
                    return;
                }

                final long commonBlockId = chainBlockIds.get(0);
                final EcBlock commonEcBlock = blockchain.getBlock(commonBlockId);
                if (commonEcBlock == null || blockchain.getHeight() - commonEcBlock.getHeight() >= 720) {
                    if (commonEcBlock != null) {
                        LoggerUtil.logDebug(peer + " advertised chain with better difficulty, but the last common block is at height " + commonEcBlock.getHeight());
                    }
                    return;
                }
                if (Constants.SIMULATE_ENDLESS_DOWNLOAD) {
                    isDownloading = true;
                    return;
                }
                if (!isDownloading && lastBlockchainFeederHeight - commonEcBlock.getHeight() > 10) {
                    LoggerUtil.logInfo("EcBlockchain download in progress");
                    isDownloading = true;
                }

                blockchain.updateECLock();
                try {
                    if (betterCumulativeDifficulty.compareTo(blockchain.getLastECBlock().getCumulativeDifficulty()) <= 0) {
                        return;
                    }
                    long lastBlockId = blockchain.getLastECBlock().getECId();
                    downloadBlockchain(peer, commonEcBlock, commonEcBlock.getHeight());
                    if (blockchain.getHeight() - commonEcBlock.getHeight() <= 10) {
                        return;
                    }

                    int confirmations = 0;
                    for (Peer otherPeer : connectedPublicPeers) {
                        if (confirmations >= numberOfForkConfirmations) {
                            break;
                        }
                        if (peer.getPeerHost().equals(otherPeer.getPeerHost())) {
                            continue;
                        }
                        chainBlockIds = getBlockIdsAfterCommon(otherPeer, commonBlockId, true);
                        if (chainBlockIds.isEmpty()) {
                            continue;
                        }
                        long otherPeerCommonBlockId = chainBlockIds.get(0);
                        if (otherPeerCommonBlockId == blockchain.getLastECBlock().getECId()) {
                            confirmations++;
                            continue;
                        }
                        EcBlock otherPeerCommonEcBlock = blockchain.getBlock(otherPeerCommonBlockId);
                        if (blockchain.getHeight() - otherPeerCommonEcBlock.getHeight() >= 720) {
                            continue;
                        }
                        String otherPeerCumulativeDifficulty;
                        JSONObject otherPeerResponse = peer.send(getCumulativeDifficultyRequest);
                        if (otherPeerResponse == null || (otherPeerCumulativeDifficulty = (String) response.get("cumulativeDifficulty")) == null) {
                            continue;
                        }
                        if (new BigInteger(otherPeerCumulativeDifficulty).compareTo(blockchain.getLastECBlock().getCumulativeDifficulty()) <= 0) {
                            continue;
                        }
                        LoggerUtil.logDebug("Found a peer with better difficulty");
                        downloadBlockchain(otherPeer, otherPeerCommonEcBlock, commonEcBlock.getHeight());
                    }
                    LoggerUtil.logDebug("Got " + confirmations + " confirmations");

                    if (blockchain.getLastECBlock().getECId() != lastBlockId) {
                        long time = System.currentTimeMillis() - startTime;
                        totalTime += time;
                        int numBlocks = blockchain.getHeight() - commonEcBlock.getHeight();
                        totalBlocks += numBlocks;
                        LoggerUtil.logInfo("Downloaded " + numBlocks + " blocks in "
                                + time / 1000 + " s, " + (totalBlocks * 1000) / totalTime + " per s, "
                                + totalTime * (lastBlockchainFeederHeight - blockchain.getHeight()) / ((long) totalBlocks * 1000 * 60) + " min left");
                    } else {
                        LoggerUtil.logDebug("Did not accept peer's blocks, back to our own fork");
                    }
                } finally {
                    blockchain.updateECUnlock();
                }

            } catch (EcStopException e) {
                LoggerUtil.logInfo("EcBlockchain download stopped: " + e.getMessage());
                throw new InterruptedException("EcBlockchain download stopped");
            } catch (Exception e) {
                LoggerUtil.logError("Error in EC_BLOCKCHAIN download thread", e);
            }
        }

        private long getCommonMilestoneBlockId(Peer peer) {

            String lastMilestoneBlockId = null;

            while (true) {
                JSONObject milestoneBlockIdsRequest = new JSONObject();
                milestoneBlockIdsRequest.put("requestType", "getMilestoneBlockIds");
                if (lastMilestoneBlockId == null) {
                    milestoneBlockIdsRequest.put("lastBlockId", blockchain.getLastECBlock().getStringECId());
                } else {
                    milestoneBlockIdsRequest.put("lastMilestoneBlockId", lastMilestoneBlockId);
                }

                JSONObject response = peer.send(JSON.prepareRequest(milestoneBlockIdsRequest));
                if (response == null) {
                    return 0;
                }
                JSONArray milestoneBlockIds = (JSONArray) response.get("milestoneBlockIds");
                if (milestoneBlockIds == null) {
                    return 0;
                }
                if (milestoneBlockIds.isEmpty()) {
                    return Genesis.EC_GENESIS_BLOCK_ID;
                }
                // prevent overloading with blockIds
                if (milestoneBlockIds.size() > 20) {
                    LoggerUtil.logDebug("Obsolete or rogue peer " + peer.getPeerHost() + " sends too many milestoneBlockIds, blacklisting");
                    peer.blacklist("Too many milestoneBlockIds");
                    return 0;
                }
                if (Boolean.TRUE.equals(response.get("last"))) {
                    peerHasMore = false;
                }
                for (Object milestoneBlockId : milestoneBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String) milestoneBlockId);
                    if (EcBlockH2.hasBlock(blockId)) {
                        if (lastMilestoneBlockId == null && milestoneBlockIds.size() > 1) {
                            peerHasMore = false;
                        }
                        return blockId;
                    }
                    lastMilestoneBlockId = (String) milestoneBlockId;
                }
            }

        }

        private List<Long> getBlockIdsAfterCommon(final Peer peer, final long startBlockId, final boolean countFromStart) {
            long matchId = startBlockId;
            List<Long> blockList = new ArrayList<>(720);
            boolean matched = false;
            int limit = countFromStart ? 720 : 1440;
            while (true) {
                JSONObject request = new JSONObject();
                request.put("requestType", "getNextBlockIds");
                request.put("blockId", Long.toUnsignedString(matchId));
                request.put("limit", limit);
                JSONObject response = peer.send(JSON.prepareRequest(request));
                if (response == null) {
                    return Collections.emptyList();
                }
                JSONArray nextBlockIds = (JSONArray) response.get("nextBlockIds");
                if (nextBlockIds == null || nextBlockIds.size() == 0) {
                    break;
                }
                // prevent overloading with blockIds
                if (nextBlockIds.size() > limit) {
                    LoggerUtil.logDebug("Obsolete or rogue peer " + peer.getPeerHost() + " sends too many nextBlockIds, blacklisting");
                    peer.blacklist("Too many nextBlockIds");
                    return Collections.emptyList();
                }
                boolean matching = true;
                int count = 0;
                for (Object nextBlockId : nextBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String) nextBlockId);
                    if (matching) {
                        if (EcBlockH2.hasBlock(blockId)) {
                            matchId = blockId;
                            matched = true;
                        } else {
                            blockList.add(matchId);
                            blockList.add(blockId);
                            matching = false;
                        }
                    } else {
                        blockList.add(blockId);
                        if (blockList.size() >= 720) {
                            break;
                        }
                    }
                    if (countFromStart && ++count >= 720) {
                        break;
                    }
                }
                if (!matching || countFromStart) {
                    break;
                }
            }
            if (blockList.isEmpty() && matched) {
                blockList.add(matchId);
            }
            return blockList;
        }

        /**
         * Download the block chain
         *
         * @param   feederPeer              Peer supplying the blocks list
         * @param   commonEcBlock             Common block
         * @throws InterruptedException    Download interrupted
         */
        private void downloadBlockchain(final Peer feederPeer, final EcBlock commonEcBlock, final int startHeight) throws InterruptedException {
            Map<Long, PeerBlock> blockMap = new HashMap<>();
            //
            // Break the download into multiple segments.  The first block in each segment
            // is the common block for that segment.
            //
            List<GetNextBlocks> getList = new ArrayList<>();
            int segSize = 36;
            int stop = chainBlockIds.size() - 1;
            for (int start = 0; start < stop; start += segSize) {
                getList.add(new GetNextBlocks(chainBlockIds, start, Math.min(start + segSize, stop)));
            }
            int nextPeerIndex = ThreadLocalRandom.current().nextInt(connectedPublicPeers.size());
            long maxResponseTime = 0;
            Peer slowestPeer = null;
            //
            // Issue the getNextBlocks requests and get the results.  We will repeat
            // a request if the peer didn't respond or returned a partial block list.
            // The download will be aborted if we are unable to get a segment after
            // retrying with different peers.
            //
            download:
            while (!getList.isEmpty()) {
                //
                // Submit threads to issue 'getNextBlocks' requests.  The first segment
                // will always be sent to the feeder peer.  Subsequent segments will
                // be sent to the feeder peer if we failed trying to download the blocks
                // from another peer.  We will stop the download and process any pending
                // blocks if we are unable to download a segment from the feeder peer.
                //
                for (GetNextBlocks nextBlocks : getList) {
                    Peer peer;
                    if (nextBlocks.getRequestCount() > 1) {
                        break download;
                    }
                    if (nextBlocks.getStart() == 0 || nextBlocks.getRequestCount() != 0) {
                        peer = feederPeer;
                    } else {
                        if (nextPeerIndex >= connectedPublicPeers.size()) {
                            nextPeerIndex = 0;
                        }
                        peer = connectedPublicPeers.get(nextPeerIndex++);
                    }
                    if (nextBlocks.getPeer() == peer) {
                        break download;
                    }
                    nextBlocks.setPeer(peer);
                    Future<List<EcBlockImpl>> future = networkService.submit(nextBlocks);
                    nextBlocks.setFuture(future);
                }
                //
                // Get the results.  A peer is on a different fork if a returned
                // block is not in the block identifier list.
                //
                Iterator<GetNextBlocks> it = getList.iterator();
                while (it.hasNext()) {
                    GetNextBlocks nextBlocks = it.next();
                    List<EcBlockImpl> blockList;
                    try {
                        blockList = nextBlocks.getFuture().get();
                    } catch (ExecutionException exc) {
                        throw new RuntimeException(exc.getMessage(), exc);
                    }
                    if (blockList == null) {
                        nextBlocks.getPeer().deactivate();
                        continue;
                    }
                    Peer peer = nextBlocks.getPeer();
                    int index = nextBlocks.getStart() + 1;
                    for (EcBlockImpl block : blockList) {
                        if (block.getECId() != chainBlockIds.get(index)) {
                            break;
                        }
                        blockMap.put(block.getECId(), new PeerBlock(peer, block));
                        index++;
                    }
                    if (index > nextBlocks.getStop()) {
                        it.remove();
                    } else {
                        nextBlocks.setStart(index - 1);
                    }
                    if (nextBlocks.getResponseTime() > maxResponseTime) {
                        maxResponseTime = nextBlocks.getResponseTime();
                        slowestPeer = nextBlocks.getPeer();
                    }
                }

            }
            if (slowestPeer != null && connectedPublicPeers.size() >= Peers.ecmaxnumberofconnectedpublicpeers && chainBlockIds.size() > 360) {
                LoggerUtil.logDebug(slowestPeer.getPeerHost() + " took " + maxResponseTime + " ms, disconnecting");
                slowestPeer.deactivate();
            }
            //
            // Add the new blocks to the EC_BLOCKCHAIN.  We will stop if we encounter
            // a missing block (this will happen if an invalid block is encountered
            // when downloading the blocks)
            //
            blockchain.writeLock();
            try {
                List<EcBlockImpl> forkBlocks = new ArrayList<>();
                for (int index = 1; index < chainBlockIds.size() && blockchain.getHeight() - startHeight < 720; index++) {
                    PeerBlock peerBlock = blockMap.get(chainBlockIds.get(index));
                    if (peerBlock == null) {
                        break;
                    }
                    EcBlockImpl block = peerBlock.getBlock();
                    if (blockchain.getLastECBlock().getECId() == block.getPreviousBlockId()) {
                        try {
                            pushBlock(block);
                        } catch (BlockNotAcceptedException e) {
                            peerBlock.getPeer().blacklist(e);
                        }
                    } else {
                        forkBlocks.add(block);
                    }
                }
                //
                // Process a fork
                //
                int myForkSize = blockchain.getHeight() - startHeight;
                if (!forkBlocks.isEmpty() && myForkSize < 720) {
                    LoggerUtil.logDebug("Will process a fork of " + forkBlocks.size() + " blocks, mine is " + myForkSize);
                    processFork(feederPeer, forkBlocks, commonEcBlock);
                }
            } finally {
                blockchain.writeUnlock();
            }

        }

        private void processFork(final Peer peer, final List<EcBlockImpl> forkBlocks, final EcBlock commonEcBlock) {

            BigInteger curCumulativeDifficulty = blockchain.getLastECBlock().getCumulativeDifficulty();

            List<EcBlockImpl> myPoppedOffBlocks = popOffTo(commonEcBlock);

            int pushedForkBlocks = 0;
            if (blockchain.getLastECBlock().getECId() == commonEcBlock.getECId()) {
                for (EcBlockImpl block : forkBlocks) {
                    if (blockchain.getLastECBlock().getECId() == block.getPreviousBlockId()) {
                        try {
                            pushBlock(block);
                            pushedForkBlocks += 1;
                        } catch (BlockNotAcceptedException e) {
                            peer.blacklist(e);
                            break;
                        }
                    }
                }
            }

            if (pushedForkBlocks > 0 && blockchain.getLastECBlock().getCumulativeDifficulty().compareTo(curCumulativeDifficulty) < 0) {
                LoggerUtil.logDebug("Pop off caused by peer " + peer.getPeerHost() + ", blacklisting");
                peer.blacklist("Pop off");
                List<EcBlockImpl> peerPoppedOffBlocks = popOffTo(commonEcBlock);
                pushedForkBlocks = 0;
                for (EcBlockImpl block : peerPoppedOffBlocks) {
                    TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                }
            }

            if (pushedForkBlocks == 0) {
                LoggerUtil.logDebug("Didn't accept any blocks, pushing back my previous blocks");
                for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
                    EcBlockImpl block = myPoppedOffBlocks.remove(i);
                    try {
                        pushBlock(block);
                    } catch (BlockNotAcceptedException e) {
                        LoggerUtil.logError("Popped off block no longer acceptable: " + block.getJSONObject().toJSONString(), e);
                        break;
                    }
                }
            } else {
                LoggerUtil.logDebug("Switched to peer's fork");
                for (EcBlockImpl block : myPoppedOffBlocks) {
                    TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                }
            }

        }

    };
    private volatile boolean alreadyInitialized = false;

    private EcBlockchainProcessorImpl() {
        final int trimFrequency = PropertiesUtil.getKeyForInt("ec.trimFrequency", 0);
        blockListenerManager.addECListener(block -> {
            if (block.getHeight() % 5000 == 0) {
                LoggerUtil.logInfo("processed block " + block.getHeight());
            }
            if (Constants.TRIM_DERIVED_TABLES && block.getHeight() % trimFrequency == 0) {
                doTrimDerivedTables();
            }
        }, EcBlockchainProcessorEvent.BLOCK_SCANNED);//TODO 修剪頻率是否更改 現1000

        blockListenerManager.addECListener(block -> {
            if (Constants.TRIM_DERIVED_TABLES && block.getHeight() % trimFrequency == 0 && !isTrimming) {
                isTrimming = true;
                networkService.submit(() -> {
                    trimDerivedTables();
                    isTrimming = false;
                });
            }
            if (block.getHeight() % 5000 == 0) {
                LoggerUtil.logInfo("received block " + block.getHeight());
                if (!isDownloading || block.getHeight() % 50000 == 0) {
                    networkService.submit(H2.H2::analyzeTables);
                }
            }
        }, EcBlockchainProcessorEvent.BLOCK_PUSHED);//TODO 5000、50000是否改為trimFrequency

        blockListenerManager.addECListener(checksumListener, EcBlockchainProcessorEvent.BLOCK_PUSHED);

        blockListenerManager.addECListener(block -> H2.H2.analyzeTables(), EcBlockchainProcessorEvent.RESCAN_END);

        ThreadPool.runBeforeStart(() -> {
            alreadyInitialized = true;
            if (addGenesisBlock()) {
                scan(0, false);
            } else if (PropertiesUtil.getKeyForBoolean("ec.forceScan")) {
                scan(0, PropertiesUtil.getKeyForBoolean("ec.forceValidate"));
            } else {
                boolean rescan;
                boolean validate;
                int height;
                try (Connection con = H2.H2.getConnection();
                     Statement stmt = con.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM scan")) {
                    rs.next();
                    rescan = rs.getBoolean("rescan");
                    validate = rs.getBoolean("validate");
                    height = rs.getInt("height");
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
                if (rescan) {
                    scan(height, validate);
                }
            }
        }, false);

        if (!Constants.IS_LIGHT_CLIENT && !Constants.IS_OFFLINE) {
            ThreadPool.scheduleThread("GetMoreBlocks", getMoreBlocksThread, 1);
        }

    }

    public static EcBlockchainProcessorImpl getInstance() {
        return instance;
    }

    @Override
    public boolean addECListener(Listener<EcBlock> listener, EcBlockchainProcessorEvent eventType) {
        return blockListenerManager.addECListener(listener, eventType);
    }

    @Override
    public boolean removeECListener(Listener<EcBlock> listener, EcBlockchainProcessorEvent eventType) {
        return blockListenerManager.removeECListener(listener, eventType);
    }

    @Override
    public void registerDerivedTable(DerivedH2Table table) {
        if (alreadyInitialized) {
            throw new IllegalStateException("Too late to register table " + table + ", must have done it in EcchainWalletApplication.Init");
        }
        derivedTables.add(table);
    }

    @Override
    public void trimDerivedTables() {
        try {
            H2.H2.beginTransaction();
            doTrimDerivedTables();
            H2.H2.commitTransaction();
        } catch (Exception e) {
            LoggerUtil.logError(e.toString(), e);
            H2.H2.rollbackTransaction();
            throw e;
        } finally {
            H2.H2.endTransaction();
        }
    }

    private void doTrimDerivedTables() {
        lastTrimHeight = Math.max(blockchain.getHeight() - Constants.MAX_ROLLBACK, 0);
        if (lastTrimHeight > 0) {
            for (DerivedH2Table table : derivedTables) {
                blockchain.readECLock();
                try {
                    table.trim(lastTrimHeight);
                    H2.H2.commitTransaction();
                } finally {
                    blockchain.readECUnlock();
                }
            }
        }
    }

    List<DerivedH2Table> getDerivedTables() {
        return derivedTables;
    }

    @Override
    public Peer getLastECBlockchainFeeder() {
        return lastBlockchainFeeder;
    }

    @Override
    public int getLastECBlockchainFeederHeight() {
        return lastBlockchainFeederHeight;
    }

    @Override
    public boolean isScanning() {
        return isScanning;
    }

    @Override
    public int getInitialScanHeight() {
        return initialScanHeight;
    }

    @Override
    public boolean isDownloading() {
        return isDownloading;
    }

    @Override
    public boolean isProcessingBlock() {
        return isProcessingBlock;
    }

    @Override
    public int getMinRollbackHeight() {
        return Constants.TRIM_DERIVED_TABLES ? (lastTrimHeight > 0 ? lastTrimHeight : Math.max(blockchain.getHeight() - Constants.MAX_ROLLBACK, 0)) : 0;
    }

    @Override
    public void processPeerBlock(JSONObject request) throws EcException {
        EcBlockImpl block = EcBlockImpl.parseBlock(request);
        EcBlockImpl lastBlock = blockchain.getLastECBlock();
        if (block.getPreviousBlockId() == lastBlock.getECId()) {
            pushBlock(block);
        } else if (block.getPreviousBlockId() == lastBlock.getPreviousBlockId() && block.getTimestamp() < lastBlock.getTimestamp()) {
            blockchain.writeLock();
            try {
                if (lastBlock.getECId() != blockchain.getLastECBlock().getECId()) {
                    return; // EC_BLOCKCHAIN changed, ignore the block
                }
                EcBlockImpl previousBlock = blockchain.getBlock(lastBlock.getPreviousBlockId());
                lastBlock = popOffTo(previousBlock).get(0);
                try {
                    pushBlock(block);
                    TransactionProcessorImpl.getInstance().processLater(lastBlock.getTransactions());
                    LoggerUtil.logDebug("Last block " + lastBlock.getStringECId() + " was replaced by " + block.getStringECId());
                } catch (BlockNotAcceptedException e) {
                    LoggerUtil.logDebug("Replacement block failed to be accepted, pushing back our last block");
                    pushBlock(lastBlock);
                    TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                }
            } finally {
                blockchain.writeUnlock();
            }
        } // else ignore the block
    }

    @Override
    public List<EcBlockImpl> popOffTo(int height) {
        if (height <= 0) {
            fullReset();
        } else if (height < blockchain.getHeight()) {
            return popOffTo(blockchain.getBlockAtHeight(height));
        }
        return Collections.emptyList();
    }

    @Override
    public void fullReset() {
        blockchain.writeLock();
        try {
            try {
                setGetMoreBlocks(false);
                scheduleScan(0, false);
                //EcBlockH2.deleteBlock(Genesis.EC_GENESIS_BLOCK_ID); // fails with stack overflow in H2
                EcBlockH2.deleteAll();
                if (addGenesisBlock()) {
                    scan(0, false);
                }
            } finally {
                setGetMoreBlocks(true);
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    @Override
    public void setGetMoreBlocks(boolean getMoreBlocks) {
        this.getMoreBlocks = getMoreBlocks;
    }

    @Override
    public int restorePrunedData() {
        H2.H2.beginTransaction();
        try (Connection con = H2.H2.getConnection()) {
            int now = new EcTime.EpochEcTime().getTime();
            int minTimestamp = Math.max(1, now - Constants.EC_MAX_PRUNABLE_LIFETIME);
            int maxTimestamp = Math.max(minTimestamp, now - Constants.EC_MIN_PRUNABLE_LIFETIME) - 1;
            List<PrunableTransaction> transactionList =
                    TransactionH2.selectPrunableTransactions(con, minTimestamp, maxTimestamp);
            transactionList.forEach(prunableTransaction -> {
                long id = prunableTransaction.getId();
                if ((prunableTransaction.hasPrunableAttachment() && prunableTransaction.getTransactionType().isPruned(id)) ||
                        PrunableMessage.isPruned(id, prunableTransaction.hasPrunablePlainMessage(), prunableTransaction.hasPrunableEncryptedMessage())) {
                    synchronized (prunableTransactions) {
                        prunableTransactions.add(id);
                    }
                }
            });
            if (!prunableTransactions.isEmpty()) {
                lastRestoreTime = 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            H2.H2.endTransaction();
        }
        synchronized (prunableTransactions) {
            return prunableTransactions.size();
        }
    }

    @Override
    public Transaction restorePrunedTransaction(long transactionId) {
        TransactionImpl transaction = TransactionH2.selectTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }
        boolean isPruned = false;
        for (AbstractEnclosure appendage : transaction.getAppendages(true)) {
            if ((appendage instanceof Prunable) &&
                    !((Prunable) appendage).hasPrunableData()) {
                isPruned = true;
                break;
            }
        }
        if (!isPruned) {
            return transaction;
        }
        List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(PeerService.PRUNABLE) &&
                !chkPeer.isBlacklisted() && chkPeer.getAnnouncedAddress() != null);
        if (peers.isEmpty()) {
            LoggerUtil.logDebug("Cannot find any archive peers");
            return null;
        }
        JSONObject json = new JSONObject();
        JSONArray requestList = new JSONArray();
        requestList.add(Long.toUnsignedString(transactionId));
        json.put("requestType", "getTransactions");
        json.put("transactionIds", requestList);
        JSONStreamAware request = JSON.prepareRequest(json);
        for (Peer peer : peers) {
            if (peer.getState() != PeerState.CONNECTED) {
                Peers.connectPeer(peer);
            }
            if (peer.getState() != PeerState.CONNECTED) {
                continue;
            }
            LoggerUtil.logDebug("Connected to archive peer " + peer.getPeerHost());
            JSONObject response = peer.send(request);
            if (response == null) {
                continue;
            }
            JSONArray transactions = (JSONArray) response.get("transactions");
            if (transactions == null || transactions.isEmpty()) {
                continue;
            }
            try {
                List<Transaction> processed = TransactionProcessorImpl.getInstance().restorePrunableData(transactions);
                if (processed.isEmpty()) {
                    continue;
                }
                synchronized (prunableTransactions) {
                    prunableTransactions.remove(transactionId);
                }
                return processed.get(0);
            } catch (EcNotValidExceptionEc e) {
                LoggerUtil.logError("Peer " + peer.getPeerHost() + " returned invalid prunable transaction", e);
                peer.blacklist(e);
            }
        }
        return null;
    }

    public void shutdown() {
        ThreadPool.shutdownExecutor("networkService", networkService, 5);
    }

    private void addBlock(EcBlockImpl block) {
        try (Connection con = H2.H2.getConnection()) {
            EcBlockH2.saveBlock(con, block);
            blockchain.setLastBlock(block);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private boolean addGenesisBlock() {//TODO 添加創世塊 添加初始化流水
        if (EcBlockH2.hasBlock(Genesis.EC_GENESIS_BLOCK_ID, 0)) {
            LoggerUtil.logInfo("Genesis block already in database");
            EcBlockImpl lastBlock = EcBlockH2.findLastBlock();
            blockchain.setLastBlock(lastBlock);
            popOffTo(lastBlock);
            LoggerUtil.logInfo("Last block height: " + lastBlock.getHeight());
            return false;
        }
        LoggerUtil.logInfo("Genesis block not in database, starting from scratch");
        try {
            List<TransactionImpl> transactions = new ArrayList<>();
            long firstPrice = 0L;
            for (int i = 0; i < Genesis.EC_GENESIS_RECIPIENTS.length; i++) {
                firstPrice = firstPrice + Genesis.EC_GENESIS_AMOUNTS[i];
                TransactionImpl transaction = new TransactionImpl.BuilderImpl((byte) 0, Genesis.EC_CREATOR_PUBLIC_KEY,
                        Genesis.EC_GENESIS_AMOUNTS[i] * Constants.ONE_EC, 0, (short) 0,
                        Mortgaged.ORDINARY_PAYMENT)
                        .timestamp(0)
                        .recipientId(Genesis.EC_GENESIS_RECIPIENTS[i])
                        .signature(Genesis.EC_GENESIS_SIGNATURES[i])
                        .height(0)
                        .ecBlockHeight(0)
                        .ecBlockId(0)
                        .build();
                transactions.add(transaction);
            }
            Collections.sort(transactions, Comparator.comparingLong(Transaction::getTransactionId));
            MessageDigest digest = Crypto.sha256();
            for (TransactionImpl transaction : transactions) {
                digest.update(transaction.bytes());
            }
            EcBlockImpl genesisBlock = new EcBlockImpl(-1, 0, 0, firstPrice*Constants.ONE_EC, 0, transactions.size() * 128, digest.digest(),
                    Genesis.EC_CREATOR_PUBLIC_KEY, new byte[64], Genesis.EC_GENESIS_BLOCK_SIGNATURE, null, transactions);
            genesisBlock.setPrevious(null);
            LoggerUtil.logInfo(genesisBlock.getJSONObject().toJSONString());
            addBlock(genesisBlock);
            return true;
        } catch (EcValidationException e) {
            LoggerUtil.logInfo(e.getMessage());
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void pushBlock(final EcBlockImpl block) throws BlockNotAcceptedException {

        int curTime = new EcTime.EpochEcTime().getTime();

        blockchain.writeLock();
        try {
            EcBlockImpl previousLastBlock = null;
            try {
                H2.H2.beginTransaction();
                previousLastBlock = blockchain.getLastECBlock();

                validate(block, previousLastBlock, curTime);

                long nextHitTime = FoundryMachine.getNextHitTime(previousLastBlock.getECId(), curTime);
                if (nextHitTime > 0 && block.getTimestamp() > nextHitTime + 1) {
                    String msg = "Rejecting block " + block.getStringECId() + " at height " + previousLastBlock.getHeight()
                            + " block timestamp " + block.getTimestamp() + " next hit time " + nextHitTime
                            + " current time " + curTime;
                    LoggerUtil.logDebug(msg);
                    FoundryMachine.setDelay(-Constants.FORGING_SPEEDUP);
                    throw new BlockOutOfOrderException(msg, block);
                }

                Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
                List<TransactionImpl> validPhasedTransactions = new ArrayList<>();
                List<TransactionImpl> invalidPhasedTransactions = new ArrayList<>();
                validatePhasedTransactions(previousLastBlock.getHeight(), validPhasedTransactions, invalidPhasedTransactions, duplicates);
                validateTransactions(block, previousLastBlock, curTime, duplicates, previousLastBlock.getHeight() >= Constants.EC_LAST_CHECKSUM_BLOCK);

                block.setPrevious(previousLastBlock);
                blockListenerManager.notify(block, EcBlockchainProcessorEvent.BEFORE_BLOCK_ACCEPT);
                TransactionProcessorImpl.getInstance().requeueAllUnconfirmedTransactions();
                addBlock(block);
                accept(block, validPhasedTransactions, invalidPhasedTransactions, duplicates);

                H2.H2.commitTransaction();
            } catch (Exception e) {
                H2.H2.rollbackTransaction();
                blockchain.setLastBlock(previousLastBlock);
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
            blockListenerManager.notify(block, EcBlockchainProcessorEvent.AFTER_BLOCK_ACCEPT);
        } finally {
            blockchain.writeUnlock();
        }

        if (block.getTimestamp() >= curTime - 600) {
            Peers.sendToSomePeers(block);
        }

        blockListenerManager.notify(block, EcBlockchainProcessorEvent.BLOCK_PUSHED);

    }

    private void validatePhasedTransactions(int height, List<TransactionImpl> validPhasedTransactions, List<TransactionImpl> invalidPhasedTransactions,
                                            Map<TransactionType, Map<String, Integer>> duplicates) {
        if (height >= Constants.EC_PHASING_BLOCK) {
            try (H2Iterator<TransactionImpl> phasedTransactions = PhasingPoll.getFinishingTransactions(height + 1)) {
                for (TransactionImpl phasedTransaction : phasedTransactions) {
                    if (height > Constants.EC_SHUFFLING_BLOCK && PhasingPoll.getResult(phasedTransaction.getTransactionId()) != null) {
                        continue;
                    }
                    try {
                        phasedTransaction.validate();
                        if (!phasedTransaction.attachmentIsDuplicate(duplicates, false)) {
                            validPhasedTransactions.add(phasedTransaction);
                        } else {
                            LoggerUtil.logDebug("At height " + height + " phased transaction " + phasedTransaction.getStringId() + " is duplicate, will not apply");
                            invalidPhasedTransactions.add(phasedTransaction);
                        }
                    } catch (EcValidationException e) {
                        LoggerUtil.logDebug("At height " + height + " phased transaction " + phasedTransaction.getStringId() + " no longer passes validation: "
                                + e.getMessage() + ", will not apply");
                        invalidPhasedTransactions.add(phasedTransaction);
                    }
                }
            }
        }
    }

    private void validate(EcBlockImpl block, EcBlockImpl previousLastBlock, int curTime) throws BlockNotAcceptedException {
        if (previousLastBlock.getECId() != block.getPreviousBlockId()) {
            throw new BlockOutOfOrderException("Previous block Id doesn't match", block);
        }
        if (block.getECVersion() != getBlockVersion(previousLastBlock.getHeight())) {
            throw new BlockNotAcceptedException("Invalid version " + block.getECVersion(), block);
        }
        if (block.getTimestamp() > curTime + Constants.EC_MAX_TIMEDRIFT) {
            LoggerUtil.logInfo("Received block " + block.getStringECId() + " from the future, timestamp " + block.getTimestamp()
                    + " generator " + Long.toUnsignedString(block.getFoundryId()) + " current time " + curTime + ", system clock may be off");
            throw new BlockOutOfOrderException("Invalid timestamp: " + block.getTimestamp()
                    + " current time is " + curTime, block);
        }
        if (block.getTimestamp() <= previousLastBlock.getTimestamp()) {
            throw new BlockNotAcceptedException("EcBlock timestamp " + block.getTimestamp() + " is before previous block timestamp "
                    + previousLastBlock.getTimestamp(), block);
        }
        if (block.getECVersion() != 1 && !Arrays.equals(Crypto.sha256().digest(previousLastBlock.bytes()), block.getPreviousBlockHash())) {
            throw new BlockNotAcceptedException("Previous block hash doesn't match", block);
        }
        if (block.getECId() == 0L || EcBlockH2.hasBlock(block.getECId(), previousLastBlock.getHeight())) {
            throw new BlockNotAcceptedException("Duplicate block or invalid Id", block);
        }
        if (!block.verifyGenerationSignature()) {
            Account generatorAccount = Account.getAccount(block.getFoundryId());
            long generatorBalance = generatorAccount == null ? 0 : generatorAccount.getEffectiveBalanceEC();
            throw new BlockNotAcceptedException("Generation signature verification failed, effective balance " + generatorBalance, block);
        }
        if (!block.verifyBlockSignature()) {
            throw new BlockNotAcceptedException("EcBlock signature verification failed", block);
        }
        if (block.getTransactions().size() > Constants.EC_MAX_NUMBER_OF_TRANSACTIONS) {
            throw new BlockNotAcceptedException("Invalid block transaction count " + block.getTransactions().size(), block);
        }
        if (block.getPayloadLength() > Constants.EC_MAX_PAYLOAD_LENGTH || block.getPayloadLength() < 0) {
            throw new BlockNotAcceptedException("Invalid block payload length " + block.getPayloadLength(), block);
        }
    }

    private void validateTransactions(EcBlockImpl block, EcBlockImpl previousLastBlock, int curTime, Map<TransactionType, Map<String, Integer>> duplicates,
                                      boolean fullValidation) throws BlockNotAcceptedException {
        long payloadLength = 0;
        long calculatedTotalAmount = 0;
        long calculatedTotalFee = 0;
        MessageDigest digest = Crypto.sha256();
        boolean hasPrunedTransactions = false;
        for (TransactionImpl transaction : block.getTransactions()) {
            if (transaction.getTimestamp() > curTime + Constants.EC_MAX_TIMEDRIFT) {
                throw new BlockOutOfOrderException("Invalid transaction timestamp: " + transaction.getTimestamp()
                        + ", current time is " + curTime, block);
            }
            if (!transaction.verifySignature()) {
                throw new TransactionNotAcceptedException("Transaction signature verification failed at height " + previousLastBlock.getHeight(), transaction);
            }
            if (fullValidation) {
                // cfb: EcBlock 303 contains a transaction which expired before the block timestamp
                if (transaction.getTimestamp() > block.getTimestamp() + Constants.EC_MAX_TIMEDRIFT
                        || (transaction.getExpiration() < block.getTimestamp() && previousLastBlock.getHeight() != 303)) {
                    throw new TransactionNotAcceptedException("Invalid transaction timestamp " + transaction.getTimestamp()
                            + ", current time is " + curTime + ", block timestamp is " + block.getTimestamp(), transaction);
                }
                if (TransactionH2.hasTransaction(transaction.getTransactionId(), previousLastBlock.getHeight())) {
                    throw new TransactionNotAcceptedException("Transaction is already in the EC_BLOCKCHAIN", transaction);
                }
                if (transaction.referencedTransactionFullHash() != null) {
                    if ((previousLastBlock.getHeight() < Constants.EC_REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                            && !TransactionH2.hasTransaction(Convert.fullhashtoid(transaction.referencedTransactionFullHash()), previousLastBlock.getHeight()))
                            || (previousLastBlock.getHeight() >= Constants.EC_REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                            && !hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0))) {
                        throw new TransactionNotAcceptedException("Missing or invalid referenced transaction "
                                + transaction.getReferencedTransactionFullHash(), transaction);
                    }
                }
                if (transaction.getVersion() != getTransactionVersion(previousLastBlock.getHeight())) {
                    throw new TransactionNotAcceptedException("Invalid transaction version " + transaction.getVersion()
                            + " at height " + previousLastBlock.getHeight(), transaction);
                }
                if (transaction.getTransactionId() == 0L) {
                    throw new TransactionNotAcceptedException("Invalid transaction Id 0", transaction);
                }
                try {
                    transaction.validate();
                } catch (EcValidationException e) {
                    throw new TransactionNotAcceptedException(e.getMessage(), transaction);
                }
            }
            if (transaction.attachmentIsDuplicate(duplicates, true)) {
                throw new TransactionNotAcceptedException("Transaction is a duplicate", transaction);
            }
            if (!hasPrunedTransactions) {
                for (AbstractEnclosure appendage : transaction.getAppendages()) {
                    if ((appendage instanceof Prunable) && !((Prunable) appendage).hasPrunableData()) {
                        hasPrunedTransactions = true;
                        break;
                    }
                }
            }
            calculatedTotalAmount += transaction.getAmountNQT();
            calculatedTotalFee += transaction.getFeeNQT();
            payloadLength += transaction.getFullSize();
            digest.update(transaction.bytes());
        }
        if (calculatedTotalAmount != block.getTotalAmountNQT() || calculatedTotalFee != block.getTotalFeeNQT()) {
            throw new BlockNotAcceptedException("Total amount or fee don't match transaction totals", block);
        }
        if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
            throw new BlockNotAcceptedException("Payload hash doesn't match", block);
        }
        if (hasPrunedTransactions ? payloadLength > block.getPayloadLength() : payloadLength != block.getPayloadLength()) {
            throw new BlockNotAcceptedException("Transaction payload length " + payloadLength + " does not match block payload length "
                    + block.getPayloadLength(), block);
        }
    }

    private void accept(EcBlockImpl block, List<TransactionImpl> validPhasedTransactions, List<TransactionImpl> invalidPhasedTransactions,
                        Map<TransactionType, Map<String, Integer>> duplicates) throws TransactionNotAcceptedException {
        try {
            isProcessingBlock = true;
            for (TransactionImpl transaction : block.getTransactions()) {
                if (!transaction.applyUnconfirmed()) {
                    throw new TransactionNotAcceptedException("Double spending", transaction);
                }
            }
            blockListenerManager.notify(block, EcBlockchainProcessorEvent.BEFORE_BLOCK_APPLY);
            block.apply();
            validPhasedTransactions.forEach(transaction -> transaction.getPhasing().countVotes(transaction));
            invalidPhasedTransactions.forEach(transaction -> transaction.getPhasing().reject(transaction));
            int fromTimestamp = new EcTime.EpochEcTime().getTime() - Constants.EC_MAX_PRUNABLE_LIFETIME;
            for (TransactionImpl transaction : block.getTransactions()) {
                try {
                    transaction.apply();
                    if (transaction.getTimestamp() > fromTimestamp) {
                        for (AbstractEnclosure appendage : transaction.getAppendages(true)) {
                            if ((appendage instanceof Prunable) &&
                                    !((Prunable) appendage).hasPrunableData()) {
                                synchronized (prunableTransactions) {
                                    prunableTransactions.add(transaction.getTransactionId());
                                }
                                lastRestoreTime = 0;
                                break;
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    LoggerUtil.logError(e.toString(), e);
                    throw new TransactionNotAcceptedException(e, transaction);
                }
            }
            if (block.getHeight() > Constants.EC_SHUFFLING_BLOCK) {
                SortedSet<TransactionImpl> possiblyApprovedTransactions = new TreeSet<>(FINISHING_TRANSACTIONS_COMPARATOR);
                block.getTransactions().forEach(transaction -> {
                    PhasingPoll.getLinkedPhasedTransactions(transaction.fullHash()).forEach(phasedTransaction -> {
                        if (phasedTransaction.getPhasing().getFinishHeight() > block.getHeight()) {
                            possiblyApprovedTransactions.add((TransactionImpl) phasedTransaction);
                        }
                    });
                    if (transaction.getTransactionType() == Messaging.PHASING_VOTE_CASTING && !transaction.attachmentIsPhased()) {
                        Mortgaged.MessagingPhasingVoteCasting voteCasting = (Mortgaged.MessagingPhasingVoteCasting) transaction.getAttachment();
                        voteCasting.getTransactionFullHashes().forEach(hash -> {
                            PhasingPoll phasingPoll = PhasingPoll.getPoll(Convert.fullhashtoid(hash));
                            if (phasingPoll.allowEarlyFinish() && phasingPoll.getFinishHeight() > block.getHeight()) {
                                possiblyApprovedTransactions.add(TransactionH2.selectTransaction(phasingPoll.getId()));
                            }
                        });
                    }
                });
                validPhasedTransactions.forEach(phasedTransaction -> {
                    if (phasedTransaction.getTransactionType() == Messaging.PHASING_VOTE_CASTING) {
                        PhasingPoll.PhasingPollResult result = PhasingPoll.getResult(phasedTransaction.getTransactionId());
                        if (result != null && result.isApproved()) {
                            Mortgaged.MessagingPhasingVoteCasting phasingVoteCasting = (Mortgaged.MessagingPhasingVoteCasting) phasedTransaction.getAttachment();
                            phasingVoteCasting.getTransactionFullHashes().forEach(hash -> {
                                PhasingPoll phasingPoll = PhasingPoll.getPoll(Convert.fullhashtoid(hash));
                                if (phasingPoll.allowEarlyFinish() && phasingPoll.getFinishHeight() > block.getHeight()) {
                                    possiblyApprovedTransactions.add(TransactionH2.selectTransaction(phasingPoll.getId()));
                                }
                            });
                        }
                    }
                });
                possiblyApprovedTransactions.forEach(transaction -> {
                    if (PhasingPoll.getResult(transaction.getTransactionId()) == null) {
                        try {
                            transaction.validate();
                            transaction.getPhasing().tryCountVotes(transaction, duplicates);
                        } catch (EcValidationException e) {
                            LoggerUtil.logDebug("At height " + block.getHeight() + " phased transaction " + transaction.getStringId()
                                    + " no longer passes validation: " + e.getMessage() + ", cannot finish early");
                        }
                    }
                });
            }
            blockListenerManager.notify(block, EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);
            if (block.getTransactions().size() > 0) {
                TransactionProcessorImpl.getInstance().notifyListeners(block.getTransactions(), TransactionProcessorEvent.ADDED_CONFIRMED_TRANSACTIONS);
            }
            AccountLedger.commitEntries();
        } finally {
            isProcessingBlock = false;
            AccountLedger.clearEntries();
        }
    }

    List<EcBlockImpl> popOffTo(EcBlock commonEcBlock) {
        blockchain.writeLock();
        try {
            if (!H2.H2.isInTransaction()) {
                try {
                    H2.H2.beginTransaction();
                    return popOffTo(commonEcBlock);
                } finally {
                    H2.H2.endTransaction();
                }
            }
            if (commonEcBlock.getHeight() < getMinRollbackHeight()) {
                LoggerUtil.logInfo("Rollback to height " + commonEcBlock.getHeight() + " not supported, will do a full rescan");
                popOffWithRescan(commonEcBlock.getHeight() + 1);
                return Collections.emptyList();
            }
            if (!blockchain.hasBlock(commonEcBlock.getECId())) {
                LoggerUtil.logDebug("EcBlock " + commonEcBlock.getStringECId() + " not found in EC_BLOCKCHAIN, nothing to pop off");
                return Collections.emptyList();
            }
            List<EcBlockImpl> poppedOffBlocks = new ArrayList<>();
            try {
                EcBlockImpl block = blockchain.getLastECBlock();
                block.loadTransactions();
                LoggerUtil.logDebug("Rollback from block " + block.getStringECId() + " at height " + block.getHeight()
                        + " to " + commonEcBlock.getStringECId() + " at " + commonEcBlock.getHeight());
                while (block.getECId() != commonEcBlock.getECId() && block.getECId() != Genesis.EC_GENESIS_BLOCK_ID) {
                    poppedOffBlocks.add(block);
                    block = popLastBlock();
                }
                for (DerivedH2Table table : derivedTables) {
                    table.rollback(commonEcBlock.getHeight());
                }
                H2.H2.clearCache();
                H2.H2.commitTransaction();
            } catch (RuntimeException e) {
                LoggerUtil.logError("Error popping off to " + commonEcBlock.getHeight() + ", " + e.toString());
                H2.H2.rollbackTransaction();
                EcBlockImpl lastBlock = EcBlockH2.findLastBlock();
                blockchain.setLastBlock(lastBlock);
                popOffTo(lastBlock);
                throw e;
            }
            return poppedOffBlocks;
        } finally {
            blockchain.writeUnlock();
        }
    }

    private EcBlockImpl popLastBlock() {
        EcBlockImpl block = blockchain.getLastECBlock();
        if (block.getECId() == Genesis.EC_GENESIS_BLOCK_ID) {
            throw new RuntimeException("Cannot pop off genesis block");
        }
        EcBlockImpl previousBlock = EcBlockH2.deleteBlocksFrom(block.getECId());
        previousBlock.loadTransactions();
        blockchain.setLastBlock(previousBlock);
        blockListenerManager.notify(block, EcBlockchainProcessorEvent.BLOCK_POPPED);
        return previousBlock;
    }

    private void popOffWithRescan(int height) {
        blockchain.writeLock();
        try {
            try {
                scheduleScan(0, false);
                EcBlockImpl lastBLock = EcBlockH2.deleteBlocksFrom(EcBlockH2.findBlockIdAtHeight(height));
                blockchain.setLastBlock(lastBLock);
                LoggerUtil.logInfo("Deleted blocks starting from height " + height);
            } finally {
                scan(0, false);
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    private int getBlockVersion(int previousBlockHeight) {
        return previousBlockHeight < Constants.EC_TRANSPARENT_FORGING_BLOCK ? 1
                : previousBlockHeight < Constants.EC_NQT_BLOCK ? 2
                : 3;
    }

    private int getTransactionVersion(int previousBlockHeight) {
        return previousBlockHeight < Constants.EC_DIGITAL_GOODS_STORE_BLOCK ? 0 : 1;
    }

    SortedSet<UnconfirmedTransaction> selectUnconfirmedTransactions(Map<TransactionType, Map<String, Integer>> duplicates, EcBlock previousEcBlock, int blockTimestamp) {
        List<UnconfirmedTransaction> orderedUnconfirmedTransactions = new ArrayList<>();
        try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
                TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions(),
                transaction -> hasAllReferencedTransactions(transaction.getTransaction(), transaction.getTimestamp(), 0))) {
            for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                orderedUnconfirmedTransactions.add(unconfirmedTransaction);
            }
        }

        SortedSet<UnconfirmedTransaction> sortedTransactions = new TreeSet<>(TRANSACTION_ARRIVAL_COMPARATOR);
        int payloadLength = 0;
        while (payloadLength <= Constants.EC_MAX_PAYLOAD_LENGTH && sortedTransactions.size() <= Constants.EC_MAX_NUMBER_OF_TRANSACTIONS) {
            int prevNumberOfNewTransactions = sortedTransactions.size();
            for (UnconfirmedTransaction unconfirmedTransaction : orderedUnconfirmedTransactions) {
                int transactionLength = unconfirmedTransaction.getTransaction().getFullSize();
                if (sortedTransactions.contains(unconfirmedTransaction) || payloadLength + transactionLength > Constants.EC_MAX_PAYLOAD_LENGTH) {
                    continue;
                }
                if (unconfirmedTransaction.getVersion() != getTransactionVersion(previousEcBlock.getHeight())) {
                    continue;
                }
                if (blockTimestamp > 0 && (unconfirmedTransaction.getTimestamp() > blockTimestamp + Constants.EC_MAX_TIMEDRIFT
                        || unconfirmedTransaction.getExpiration() < blockTimestamp)) {
                    continue;
                }
                try {
                    unconfirmedTransaction.getTransaction().validate();
                } catch (EcValidationException e) {
                    continue;
                }
                if (unconfirmedTransaction.getTransaction().attachmentIsDuplicate(duplicates, true)) {
                    continue;
                }
                sortedTransactions.add(unconfirmedTransaction);
                payloadLength += transactionLength;
            }
            if (sortedTransactions.size() == prevNumberOfNewTransactions) {
                break;
            }
        }
        return sortedTransactions;
    }

    void generateBlock(String secretPhrase, int blockTimestamp) throws BlockNotAcceptedException {
        LoggerUtil.logInfo("铸造块....");
        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        if (blockchain.getHeight() >= Constants.EC_PHASING_BLOCK) {
            try (H2Iterator<TransactionImpl> phasedTransactions = PhasingPoll.getFinishingTransactions(blockchain.getHeight() + 1)) {
                for (TransactionImpl phasedTransaction : phasedTransactions) {
                    try {
                        phasedTransaction.validate();
                        phasedTransaction.attachmentIsDuplicate(duplicates, false); // pre-populate duplicates map
                    } catch (EcValidationException ignore) {
                    }
                }
            }
        }

        EcBlockImpl previousBlock = blockchain.getLastECBlock();
        TransactionProcessorImpl.getInstance().processWaitingTransactions();
        SortedSet<UnconfirmedTransaction> sortedTransactions = selectUnconfirmedTransactions(duplicates, previousBlock, blockTimestamp);
        List<TransactionImpl> blockTransactions = new ArrayList<>();
        MessageDigest digest = Crypto.sha256();
        long totalAmountNQT = 0;
        long totalFeeNQT = 0;
        int payloadLength = 0;
        for (UnconfirmedTransaction unconfirmedTransaction : sortedTransactions) {
            TransactionImpl transaction = unconfirmedTransaction.getTransaction();
            blockTransactions.add(transaction);
            digest.update(transaction.bytes());
            totalAmountNQT += transaction.getAmountNQT();
            totalFeeNQT += transaction.getFeeNQT();
            payloadLength += transaction.getFullSize();
        }
        byte[] payloadHash = digest.digest();
        digest.update(previousBlock.getFoundrySignature());
        final byte[] publicKey = Crypto.getPublicKey(secretPhrase);
        byte[] generationSignature = digest.digest(publicKey);
        byte[] previousBlockHash = Crypto.sha256().digest(previousBlock.bytes());

        EcBlockImpl block = new EcBlockImpl(getBlockVersion(previousBlock.getHeight()), blockTimestamp, previousBlock.getECId(), totalAmountNQT, totalFeeNQT, payloadLength,
                payloadHash, publicKey, generationSignature, previousBlockHash, blockTransactions, secretPhrase);//生成快

        try {
            pushBlock(block);//驗證并生成塊
            blockListenerManager.notify(block, EcBlockchainProcessorEvent.BLOCK_GENERATED);
            LoggerUtil.logDebug("Account " + Long.toUnsignedString(block.getFoundryId()) + " generated block " + block.getStringECId()
                    + " at height " + block.getHeight() + " timestamp " + block.getTimestamp() + " fee " + ((float) block.getTotalFeeNQT()) / Constants.ONE_EC);
        } catch (TransactionNotAcceptedException e) {
            LoggerUtil.logDebug("Generate block failed: " + e.getMessage());
            TransactionProcessorImpl.getInstance().processWaitingTransactions();
            TransactionImpl transaction = e.getTransactionImpl();
            LoggerUtil.logDebug("Removing invalid transaction: " + transaction.getStringId());
            blockchain.writeLock();
            try {
                TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
            } finally {
                blockchain.writeUnlock();
            }
            throw e;
        } catch (BlockNotAcceptedException e) {
            LoggerUtil.logDebug("Generate block failed: " + e.getMessage());
            throw e;
        }
    }

    boolean hasAllReferencedTransactions(TransactionImpl transaction, int timestamp, int count) {
        if (transaction.referencedTransactionFullHash() == null) {
            return timestamp - transaction.getTimestamp() < Constants.EC_MAX_REFERENCED_TRANSACTION_TIMESPAN && count < 10;
        }
        TransactionImpl referencedTransaction = TransactionH2.selectTransactionByFullHash(transaction.referencedTransactionFullHash());
        return referencedTransaction != null
                && referencedTransaction.getTransactionHeight() < transaction.getTransactionHeight()
                && hasAllReferencedTransactions(referencedTransaction, timestamp, count + 1);
    }

    void scheduleScan(int height, boolean validate) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("UPDATE scan SET rescan = TRUE, height = ?, validate = ?")) {
            pstmt.setInt(1, height);
            pstmt.setBoolean(2, validate);
            pstmt.executeUpdate();
            LoggerUtil.logDebug("Scheduled scan starting from height " + height + (validate ? ", with validation" : ""));
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void scan(int height, boolean validate) {
        scan(height, validate, false);
    }

    @Override
    public void fullScanWithShutdown() {
        scan(0, true, true);
    }

    private void scan(int height, boolean validate, boolean shutdown) {
        blockchain.writeLock();
        try {
            if (!H2.H2.isInTransaction()) {
                try {
                    H2.H2.beginTransaction();
                    if (validate) {
                        blockListenerManager.addECListener(checksumListener, EcBlockchainProcessorEvent.BLOCK_SCANNED);
                    }
                    scan(height, validate, shutdown);
                    H2.H2.commitTransaction();
                } catch (Exception e) {
                    H2.H2.rollbackTransaction();
                    throw e;
                } finally {
                    H2.H2.endTransaction();
                    blockListenerManager.removeECListener(checksumListener, EcBlockchainProcessorEvent.BLOCK_SCANNED);
                }
                return;
            }
            scheduleScan(height, validate);
            if (height > 0 && height < getMinRollbackHeight()) {
                LoggerUtil.logInfo("Rollback to height less than " + getMinRollbackHeight() + " not supported, will do a full scan");
                height = 0;
            }
            if (height < 0) {
                height = 0;
            }
            LoggerUtil.logInfo("Scanning EC_BLOCKCHAIN starting from height " + height + "...");
            if (validate) {
                LoggerUtil.logDebug("Also verifying signatures and validating transactions...");
            }
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmtSelect = con.prepareStatement("SELECT * FROM block WHERE " + (height > 0 ? "height >= ? AND " : "")
                         + " db_id >= ? ORDER BY db_id ASC LIMIT 50000");
                 PreparedStatement pstmtDone = con.prepareStatement("UPDATE scan SET rescan = FALSE, height = 0, validate = FALSE")) {
                isScanning = true;
                initialScanHeight = blockchain.getHeight();
                if (height > blockchain.getHeight() + 1) {
                    LoggerUtil.logInfo("Rollback height " + (height - 1) + " exceeds current EC_BLOCKCHAIN height of " + blockchain.getHeight() + ", no scan needed");
                    pstmtDone.executeUpdate();
                    H2.H2.commitTransaction();
                    return;
                }
                if (height == 0) {
                    LoggerUtil.logDebug("Dropping all full text search indexes");
                    FullTextTrigger.delAll(con);
                }
                for (DerivedH2Table table : derivedTables) {
                    if (height == 0) {
                        table.truncate();
                    } else {
                        table.rollback(height - 1);
                    }
                }
                H2.H2.clearCache();
                H2.H2.commitTransaction();
                LoggerUtil.logDebug("Rolled back derived tables");
                EcBlockImpl currentBlock = EcBlockH2.findBlockAtHeight(height);
                blockListenerManager.notify(currentBlock, EcBlockchainProcessorEvent.RESCAN_BEGIN);
                long currentBlockId = currentBlock.getECId();
                if (height == 0) {
                    blockchain.setLastBlock(currentBlock); // special case to avoid no last block
                    Account.addOrGetAccount(Genesis.EC_CREATOR_ID).apply(Genesis.EC_CREATOR_PUBLIC_KEY);
                } else {
                    blockchain.setLastBlock(EcBlockH2.findBlockAtHeight(height - 1));
                }
                if (shutdown) {
                    LoggerUtil.logInfo("Scan will be performed at next start");
                    new Thread(() -> System.exit(0)).start();
                    return;
                }
                int pstmtSelectIndex = 1;
                if (height > 0) {
                    pstmtSelect.setInt(pstmtSelectIndex++, height);
                }
                long dbId = Long.MIN_VALUE;
                boolean hasMore = true;
                outer:
                while (hasMore) {
                    hasMore = false;
                    pstmtSelect.setLong(pstmtSelectIndex, dbId);
                    try (ResultSet rs = pstmtSelect.executeQuery()) {
                        while (rs.next()) {
                            try {
                                dbId = rs.getLong("db_id");
                                currentBlock = EcBlockH2.loadBlock(con, rs, true);
                                currentBlock.loadTransactions();
                                if (currentBlock.getECId() != currentBlockId || currentBlock.getHeight() > blockchain.getHeight() + 1) {
                                    throw new EcNotValidExceptionEc("Database blocks in the wrong order!");
                                }
                                Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
                                List<TransactionImpl> validPhasedTransactions = new ArrayList<>();
                                List<TransactionImpl> invalidPhasedTransactions = new ArrayList<>();
                                validatePhasedTransactions(blockchain.getHeight(), validPhasedTransactions, invalidPhasedTransactions, duplicates);
                                if (validate && currentBlockId != Genesis.EC_GENESIS_BLOCK_ID) {
                                    int curTime = new EcTime.EpochEcTime().getTime();
                                    validate(currentBlock, blockchain.getLastECBlock(), curTime);
                                    byte[] blockBytes = currentBlock.bytes();
                                    JSONObject blockJSON = (JSONObject) JSONValue.parse(currentBlock.getJSONObject().toJSONString());
                                    if (!Arrays.equals(blockBytes, EcBlockImpl.parseBlock(blockJSON).bytes())) {
                                        throw new EcNotValidExceptionEc("EcBlock JSON cannot be parsed back to the same block");
                                    }
                                    validateTransactions(currentBlock, blockchain.getLastECBlock(), curTime, duplicates, true);
                                    for (TransactionImpl transaction : currentBlock.getTransactions()) {
                                        byte[] transactionBytes = transaction.bytes();
                                        if (currentBlock.getHeight() > Constants.EC_NQT_BLOCK
                                                && !Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionBytes).build().bytes())) {
                                            throw new EcNotValidExceptionEc("Transaction bytes cannot be parsed back to the same transaction: "
                                                    + transaction.getJSONObject().toJSONString());
                                        }
                                        JSONObject transactionJSON = (JSONObject) JSONValue.parse(transaction.getJSONObject().toJSONString());
                                        if (!Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionJSON).build().bytes())) {
                                            throw new EcNotValidExceptionEc("Transaction JSON cannot be parsed back to the same transaction: "
                                                    + transaction.getJSONObject().toJSONString());
                                        }
                                    }
                                }
                                blockListenerManager.notify(currentBlock, EcBlockchainProcessorEvent.BEFORE_BLOCK_ACCEPT);
                                blockchain.setLastBlock(currentBlock);
                                accept(currentBlock, validPhasedTransactions, invalidPhasedTransactions, duplicates);
                                currentBlockId = currentBlock.getNextBlockId();
                                H2.H2.clearCache();
                                H2.H2.commitTransaction();
                                blockListenerManager.notify(currentBlock, EcBlockchainProcessorEvent.AFTER_BLOCK_ACCEPT);
                            } catch (EcException | RuntimeException e) {
                                H2.H2.rollbackTransaction();
                                LoggerUtil.logError(e.toString(), e);
                                LoggerUtil.logDebug("Applying block " + Long.toUnsignedString(currentBlockId) + " at height "
                                        + (currentBlock == null ? 0 : currentBlock.getHeight()) + " failed, deleting from database");
                                EcBlockImpl lastBlock = EcBlockH2.deleteBlocksFrom(currentBlockId);
                                blockchain.setLastBlock(lastBlock);
                                popOffTo(lastBlock);
                                break outer;
                            }
                            blockListenerManager.notify(currentBlock, EcBlockchainProcessorEvent.BLOCK_SCANNED);
                            hasMore = true;
                        }
                        dbId = dbId + 1;
                    }
                }
                if (height == 0) {
                    for (DerivedH2Table table : derivedTables) {
                        table.establishSearchIndex(con);
                    }
                }
                pstmtDone.executeUpdate();
                H2.H2.commitTransaction();
                blockListenerManager.notify(currentBlock, EcBlockchainProcessorEvent.RESCAN_END);
                LoggerUtil.logInfo("...done at height " + blockchain.getHeight());
                if (height == 0 && validate) {
                    LoggerUtil.logInfo("SUCCESSFULLY PERFORMED FULL RESCAN WITH VALIDATION");
                }
                lastRestoreTime = 0;
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } finally {
                isScanning = false;
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    private class RestorePrunableDataTask implements Runnable {

        @Override
        public void run() {
            Peer peer = null;
            try {
                //
                // Locate an archive peer
                //
                List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(PeerService.PRUNABLE) &&
                        !chkPeer.isBlacklisted() && chkPeer.getAnnouncedAddress() != null);
                while (!peers.isEmpty()) {
                    Peer chkPeer = peers.get(ThreadLocalRandom.current().nextInt(peers.size()));
                    if (chkPeer.getState() != PeerState.CONNECTED) {
                        Peers.connectPeer(chkPeer);
                    }
                    if (chkPeer.getState() == PeerState.CONNECTED) {
                        peer = chkPeer;
                        break;
                    }
                }
                if (peer == null) {
                    LoggerUtil.logDebug("Cannot find any archive peers");
                    return;
                }
                LoggerUtil.logDebug("Connected to archive peer " + peer.getPeerHost());
                //
                // Make a copy of the prunable transaction list so we can del entries
                // as we process them while still retaining the entry if we need to
                // retry later using a different archive peer
                //
                Set<Long> processing;
                synchronized (prunableTransactions) {
                    processing = new HashSet<>(prunableTransactions.size());
                    processing.addAll(prunableTransactions);
                }
                LoggerUtil.logDebug("Need to restore " + processing.size() + " pruned data");
                //
                // Request transactions in batches of 100 until all transactions have been processed
                //
                while (!processing.isEmpty()) {
                    //
                    // Get the pruned transactions from the archive peer
                    //
                    JSONObject request = new JSONObject();
                    JSONArray requestList = new JSONArray();
                    synchronized (prunableTransactions) {
                        Iterator<Long> it = processing.iterator();
                        while (it.hasNext()) {
                            long id = it.next();
                            requestList.add(Long.toUnsignedString(id));
                            it.remove();
                            if (requestList.size() == 100)
                                break;
                        }
                    }
                    request.put("requestType", "getTransactions");
                    request.put("transactionIds", requestList);
                    JSONObject response = peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
                    if (response == null) {
                        return;
                    }
                    //
                    // Restore the prunable data
                    //
                    JSONArray transactions = (JSONArray) response.get("transactions");
                    if (transactions == null || transactions.isEmpty()) {
                        return;
                    }
                    List<Transaction> processed = TransactionProcessorImpl.getInstance().restorePrunableData(transactions);
                    //
                    // Remove transactions that have been successfully processed
                    //
                    synchronized (prunableTransactions) {
                        processed.forEach(transaction -> prunableTransactions.remove(transaction.getTransactionId()));
                    }
                }
                LoggerUtil.logDebug("Done retrieving prunable transactions from " + peer.getPeerHost());
            } catch (EcValidationException e) {
                LoggerUtil.logError("Peer " + peer.getPeerHost() + " returned invalid prunable transaction", e);
                peer.blacklist(e);
            } catch (RuntimeException e) {
                LoggerUtil.logError("Unable to restore prunable data", e);
            } finally {
                isRestoring = false;
                LoggerUtil.logDebug("Remaining " + prunableTransactions.size() + " pruned transactions");
            }
        }
    }
}
