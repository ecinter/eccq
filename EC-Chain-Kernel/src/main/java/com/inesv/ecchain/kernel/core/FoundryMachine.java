package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.*;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class FoundryMachine implements Comparable<FoundryMachine> {

    private static final ListenerManager<FoundryMachine, FoundryMachineEvent> LISTENER_MANAGER = new ListenerManager<>();
    private static final ConcurrentMap<String, FoundryMachine> GENERATORS = new ConcurrentHashMap<>();
    private static final Collection<FoundryMachine> ALL_FOUNDRY_MACHINES = Collections.unmodifiableCollection(GENERATORS.values());
    private static final Set<Long> activeGeneratorIds = new HashSet<>();
    private static final List<ActiveGenerator> activeGenerators = new ArrayList<>();
    private static volatile List<FoundryMachine> sortedForgers = null;
    private static long lastBlockId;
    private static int delayTime = Constants.FORGING_DELAY;
    private static final Runnable generateBlocksThread = new Runnable() {

        private volatile boolean logged;

        @Override
        public void run() {

            try {
                try {
                    EcBlockchainImpl.getInstance().updateECLock();
                    try {
                        EcBlock lastEcBlock = EcBlockchainImpl.getInstance().getLastECBlock();
                        if (lastEcBlock == null || lastEcBlock.getHeight() < Constants.EC_LAST_KNOWN_BLOCK) {
                            return;
                        }
                        final int generationLimit = new EcTime.EpochEcTime().getTime() - delayTime;
                        if (lastEcBlock.getECId() != lastBlockId || sortedForgers == null) {
                            lastBlockId = lastEcBlock.getECId();
                            if (lastEcBlock.getTimestamp() > new EcTime.EpochEcTime().getTime() - 600) {
                                EcBlock previousEcBlock = EcBlockchainImpl.getInstance().getBlock(lastEcBlock.getPreviousBlockId());
                                for (FoundryMachine foundryMachine : GENERATORS.values()) {
                                    foundryMachine.setLastBlock(previousEcBlock);
                                    int timestamp = foundryMachine.getTimestamp(generationLimit);
                                    if (timestamp != generationLimit && foundryMachine.getHitTime() > 0 && timestamp < lastEcBlock.getTimestamp()) {
                                        LoggerUtil.logDebug("Pop off: " + foundryMachine.toString() + " will pop off last block " + lastEcBlock.getStringECId());
                                        List<EcBlockImpl> poppedOffBlock = EcBlockchainProcessorImpl.getInstance().popOffTo(previousEcBlock);
                                        for (EcBlockImpl block : poppedOffBlock) {
                                            TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                                        }
                                        lastEcBlock = previousEcBlock;
                                        lastBlockId = previousEcBlock.getECId();
                                        break;
                                    }
                                }
                            }
                            List<FoundryMachine> forgers = new ArrayList<>();
                            for (FoundryMachine foundryMachine : GENERATORS.values()) {
                                foundryMachine.setLastBlock(lastEcBlock);
                                if (foundryMachine.effectiveBalance.signum() > 0) {
                                    forgers.add(foundryMachine);
                                }
                            }
                            Collections.sort(forgers);
                            sortedForgers = Collections.unmodifiableList(forgers);
                            logged = false;
                        }
                        if (!logged) {
                            for (FoundryMachine foundryMachine : sortedForgers) {
                                if (foundryMachine.getHitTime() - generationLimit > 60) {
                                    break;
                                }
                                LoggerUtil.logDebug(foundryMachine.toString());
                                logged = true;
                            }
                        }
                        for (FoundryMachine foundryMachine : sortedForgers) {
                            if (foundryMachine.getHitTime() > generationLimit || foundryMachine.forge(lastEcBlock, generationLimit)) {
                                return;
                            }
                        }
                    } finally {
                        EcBlockchainImpl.getInstance().updateECUnlock();
                    }
                } catch (Exception e) {
                    LoggerUtil.logError("Error in block generation thread", e);
                }
            } catch (Throwable t) {
                LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }

        }

    };
    private static long activeBlockId;
    private static boolean generatorsInitialized = false;

    static {
        if (!Constants.IS_LIGHT_CLIENT) {
            ThreadPool.scheduleThread("GenerateBlocks", generateBlocksThread, 500, TimeUnit.MILLISECONDS);
        }
    }

    private final long accountId;
    private final String secretPhrase;
    private final byte[] publicKey;
    private volatile long hitTime;
    private volatile BigInteger hit;
    private volatile BigInteger effectiveBalance;
    private volatile long deadline;

    private FoundryMachine(String secretPhrase) {
        this.secretPhrase = secretPhrase;
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.accountId = Account.getId(publicKey);
        EcBlockchainImpl.getInstance().updateECLock();
        try {
            if (EcBlockchainImpl.getInstance().getHeight() >= Constants.EC_LAST_KNOWN_BLOCK) {
                setLastBlock(EcBlockchainImpl.getInstance().getLastECBlock());
            }
            sortedForgers = null;
        } finally {
            EcBlockchainImpl.getInstance().updateECUnlock();
        }
    }

    public static void start() {
    }

    public static boolean addFoundryMachineListener(Listener<FoundryMachine> listener, FoundryMachineEvent eventType) {
        return LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removeFoundryMachineListener(Listener<FoundryMachine> listener, FoundryMachineEvent eventType) {
        return LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static FoundryMachine startForging(String secretPhrase) {
        if (GENERATORS.size() >= Constants.MAX_FORGERS) {
            throw new RuntimeException("Cannot forge with more than " + Constants.MAX_FORGERS + " accounts on the same node");
        }
        FoundryMachine foundryMachine = new FoundryMachine(secretPhrase);
        FoundryMachine old = GENERATORS.putIfAbsent(secretPhrase, foundryMachine);
        if (old != null) {
            LoggerUtil.logDebug(old + " is already forging");
            return old;
        }
        LISTENER_MANAGER.notify(foundryMachine, FoundryMachineEvent.START_FORGING);
        LoggerUtil.logDebug(foundryMachine + " started");
        return foundryMachine;
    }

    public static FoundryMachine stopForging(String secretPhrase) {
        FoundryMachine foundryMachine = GENERATORS.remove(secretPhrase);
        if (foundryMachine != null) {
            EcBlockchainImpl.getInstance().updateECLock();
            try {
                sortedForgers = null;
            } finally {
                EcBlockchainImpl.getInstance().updateECUnlock();
            }
            LoggerUtil.logDebug(foundryMachine + " stopped");
            LISTENER_MANAGER.notify(foundryMachine, FoundryMachineEvent.STOP_FORGING);
        }
        return foundryMachine;
    }

    public static int stopForging() {
        int count = GENERATORS.size();
        Iterator<FoundryMachine> iter = GENERATORS.values().iterator();
        while (iter.hasNext()) {
            FoundryMachine foundryMachine = iter.next();
            iter.remove();
            LoggerUtil.logDebug(foundryMachine + " stopped");
            LISTENER_MANAGER.notify(foundryMachine, FoundryMachineEvent.STOP_FORGING);
        }
        EcBlockchainImpl.getInstance().updateECLock();
        try {
            sortedForgers = null;
        } finally {
            EcBlockchainImpl.getInstance().updateECUnlock();
        }
        return count;
    }

    public static FoundryMachine getFoundryMachine(String secretPhrase) {
        return GENERATORS.get(secretPhrase);
    }

    public static int getFoundryMachineCount() {
        return GENERATORS.size();
    }

    public static Collection<FoundryMachine> getAllFoundryMachines() {
        return ALL_FOUNDRY_MACHINES;
    }

    public static List<FoundryMachine> getSortedForgers() {
        List<FoundryMachine> forgers = sortedForgers;
        return forgers == null ? Collections.emptyList() : forgers;
    }

    public static long getNextHitTime(long lastBlockId, int curTime) {
        EcBlockchainImpl.getInstance().readECLock();
        try {
            if (lastBlockId == FoundryMachine.lastBlockId && sortedForgers != null) {
                for (FoundryMachine foundryMachine : sortedForgers) {
                    if (foundryMachine.getHitTime() >= curTime - Constants.FORGING_DELAY) {
                        return foundryMachine.getHitTime();
                    }
                }
            }
            return 0;
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
    }

    static void setDelay(int delay) {
        FoundryMachine.delayTime = delay;
    }

    static boolean verifyHit(BigInteger hit, BigInteger effectiveBalance, EcBlock previousEcBlock, int timestamp) {
        int elapsedTime = timestamp - previousEcBlock.getTimestamp();
        if (elapsedTime <= 0) {
            return false;
        }
        BigInteger effectiveBaseTarget = BigInteger.valueOf(previousEcBlock.getBaseTarget()).multiply(effectiveBalance);
        BigInteger prevTarget = effectiveBaseTarget.multiply(BigInteger.valueOf(elapsedTime - 1));
        BigInteger target = prevTarget.add(effectiveBaseTarget);
        return hit.compareTo(target) < 0
                && (previousEcBlock.getHeight() < Constants.EC_TRANSPARENT_FORGING_BLOCK_5
                || hit.compareTo(prevTarget) >= 0
                ||  elapsedTime > 3600
                || Constants.IS_OFFLINE);
    }

    static BigInteger getHit(byte[] publicKey, EcBlock ecBlock) {
        if (ecBlock.getHeight() < Constants.EC_TRANSPARENT_FORGING_BLOCK) {
            throw new IllegalArgumentException("Not supported below Transparent Forging EcBlock");
        }
        MessageDigest digest = Crypto.sha256();
        digest.update(ecBlock.getFoundrySignature());
        byte[] generationSignatureHash = digest.digest(publicKey);
        return new BigInteger(1, new byte[]{generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});
    }

    static long getHitTime(BigInteger effectiveBalance, BigInteger hit, EcBlock ecBlock) {
        return ecBlock.getTimestamp()
                + hit.divide(BigInteger.valueOf(ecBlock.getBaseTarget()).multiply(effectiveBalance)).longValue();
    }

    public static List<ActiveGenerator> getNextGenerators() {
        List<ActiveGenerator> generatorList;
        EcBlockchain ecBlockchain = EcBlockchainImpl.getInstance();
        synchronized (activeGenerators) {
            if (!generatorsInitialized) {
                activeGeneratorIds.addAll(EcBlockH2.getBlockGenerators(Math.max(1, ecBlockchain.getHeight() - 10000)));
                activeGeneratorIds.forEach(activeGeneratorId -> activeGenerators.add(new ActiveGenerator(activeGeneratorId)));
                LoggerUtil.logDebug(activeGeneratorIds.size() + " block GENERATORS found");
                EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
                    long generatorId = block.getFoundryId();
                    synchronized (activeGenerators) {
                        if (!activeGeneratorIds.contains(generatorId)) {
                            activeGeneratorIds.add(generatorId);
                            activeGenerators.add(new ActiveGenerator(generatorId));
                        }
                    }
                }, EcBlockchainProcessorEvent.BLOCK_PUSHED);
                generatorsInitialized = true;
            }
            long blockId = ecBlockchain.getLastECBlock().getECId();
            if (blockId != activeBlockId) {
                activeBlockId = blockId;
                EcBlock lastEcBlock = ecBlockchain.getLastECBlock();
                for (ActiveGenerator generator : activeGenerators) {
                    generator.setLastBlock(lastEcBlock);
                }
                Collections.sort(activeGenerators);
            }
            generatorList = new ArrayList<>(activeGenerators);
        }
        return generatorList;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getDeadline() {
        return deadline;
    }

    public long getHitTime() {
        return hitTime;
    }

    @Override
    public int compareTo(FoundryMachine g) {
        int i = this.hit.multiply(g.effectiveBalance).compareTo(g.hit.multiply(this.effectiveBalance));
        if (i != 0) {
            return i;
        }
        return Long.compare(accountId, g.accountId);
    }

    @Override
    public String toString() {
        return "Forger " + Long.toUnsignedString(accountId) + " deadline " + getDeadline() + " hit " + hitTime;
    }

    private void setLastBlock(EcBlock lastEcBlock) {
        int height = lastEcBlock.getHeight();
        Account account = Account.getAccount(accountId, height);
        if (account == null) {
            effectiveBalance = BigInteger.ZERO;
        } else {
            effectiveBalance = BigInteger.valueOf(Math.max(account.getEffectiveBalanceEC(height), 0));
        }
        if (effectiveBalance.signum() == 0) {
            hitTime = 0;
            hit = BigInteger.ZERO;
            return;
        }
        hit = getHit(publicKey, lastEcBlock);
        hitTime = getHitTime(effectiveBalance, hit, lastEcBlock);
        deadline = Math.max(hitTime - lastEcBlock.getTimestamp(), 0);
        LISTENER_MANAGER.notify(this, FoundryMachineEvent.GENERATION_DEADLINE);
    }

    boolean forge(EcBlock lastEcBlock, int generationLimit) throws BlockNotAcceptedException {
        int timestamp = getTimestamp(generationLimit);
        if (!verifyHit(hit, effectiveBalance, lastEcBlock, timestamp)) {
            LoggerUtil.logDebug(this.toString() + " failed to forge at " + timestamp + " height " + lastEcBlock.getHeight() + " last timestamp " + lastEcBlock.getTimestamp());
            return false;
        }
        int start = new EcTime.EpochEcTime().getTime();
        while (true) {
            try {
                EcBlockchainProcessorImpl.getInstance().generateBlock(secretPhrase, timestamp);
                setDelay(Constants.FORGING_DELAY);
                return true;
            } catch (TransactionNotAcceptedException e) {
                // the bad transaction has been expunged, try again
                if (new EcTime.EpochEcTime().getTime() - start > 10) { // give up after trying for 10 s
                    throw e;
                }
            }
        }
    }

    private int getTimestamp(int generationLimit) {
        return (generationLimit - hitTime > 3600) ? generationLimit : (int) hitTime + 1;
    }

    public static class ActiveGenerator implements Comparable<ActiveGenerator> {
        private final long accountId;
        private long hitTime;
        private long effectiveBalanceEC;
        private byte[] publicKey;

        public ActiveGenerator(long accountId) {
            this.accountId = accountId;
            this.hitTime = Long.MAX_VALUE;
        }

        public long getAccountId() {
            return accountId;
        }

        public long getEffectiveBalance() {
            return effectiveBalanceEC;
        }

        public long getHitTime() {
            return hitTime;
        }

        private void setLastBlock(EcBlock lastEcBlock) {
            if (publicKey == null) {
                publicKey = Account.getPublicKey(accountId);
                if (publicKey == null) {
                    hitTime = Long.MAX_VALUE;
                    return;
                }
            }
            int height = lastEcBlock.getHeight();
            Account account = Account.getAccount(accountId, height);
            if (account == null) {
                hitTime = Long.MAX_VALUE;
                return;
            }
            effectiveBalanceEC = Math.max(account.getEffectiveBalanceEC(height), 0);
            if (effectiveBalanceEC == 0) {
                hitTime = Long.MAX_VALUE;
                return;
            }
            BigInteger effectiveBalance = BigInteger.valueOf(effectiveBalanceEC);
            BigInteger hit = FoundryMachine.getHit(publicKey, lastEcBlock);
            hitTime = FoundryMachine.getHitTime(effectiveBalance, hit, lastEcBlock);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(accountId);
        }

        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof ActiveGenerator) && accountId == ((ActiveGenerator) obj).accountId);
        }

        @Override
        public int compareTo(ActiveGenerator obj) {
            return (hitTime < obj.hitTime ? -1 : (hitTime > obj.hitTime ? 1 : 0));
        }
    }
}
