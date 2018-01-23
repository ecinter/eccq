package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class EcBlockImpl implements EcBlock {

    private static final long[] badBlocks = new long[]{};

    static {
        Arrays.sort(badBlocks);
    }

    private final int version;
    private final int timestamp;
    private final long previousBlockId;
    private final byte[] previousBlockHash;
    private final long totalAmountNQT;
    private final long totalFeeNQT;
    private final int payloadLength;
    private final byte[] generationSignature;
    private final byte[] payloadHash;
    private volatile byte[] generatorPublicKey;
    private volatile List<TransactionImpl> blockTransactions;
    private byte[] blockSignature;//塊簽名
    private BigInteger cumulativeDifficulty = BigInteger.ZERO;
    private long baseTarget = Constants.EC_INITIAL_BASE_TARGET;
    private volatile long nextBlockId;
    private int height = -1;
    private volatile long id;
    private volatile String stringId = null;
    private volatile long generatorId;
    private volatile byte[] bytes = null;
    private volatile boolean hasValidSignature = false;

    EcBlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
                byte[] generatorPublicKey, byte[] generationSignature, byte[] previousBlockHash, List<TransactionImpl> transactions, String secretPhrase) {
        this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                generatorPublicKey, generationSignature, null, previousBlockHash, transactions);
        blockSignature = Crypto.ecSign(bytes(), secretPhrase);
        bytes = null;
    }

    EcBlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
                byte[] generatorPublicKey, byte[] generationSignature, byte[] blockSignature, byte[] previousBlockHash, List<TransactionImpl> transactions) {
        this.version = version;
        this.timestamp = timestamp;
        this.previousBlockId = previousBlockId;
        this.totalAmountNQT = totalAmountNQT;
        this.totalFeeNQT = totalFeeNQT;
        this.payloadLength = payloadLength;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSignature = generationSignature;
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        if (transactions != null) {
            this.blockTransactions = Collections.unmodifiableList(transactions);
        }
    }

    EcBlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength,
                byte[] payloadHash, long generatorId, byte[] generationSignature, byte[] blockSignature,
                byte[] previousBlockHash, BigInteger cumulativeDifficulty, long baseTarget, long nextBlockId, int height, long id,
                List<TransactionImpl> blockTransactions) {
        this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                null, generationSignature, blockSignature, previousBlockHash, null);
        this.cumulativeDifficulty = cumulativeDifficulty;
        this.baseTarget = baseTarget;
        this.nextBlockId = nextBlockId;
        this.height = height;
        this.id = id;
        this.generatorId = generatorId;
        this.blockTransactions = blockTransactions;
    }

    static EcBlockImpl parseBlock(JSONObject blockData) throws EcNotValidExceptionEc {
        try {
            int version = ((Long) blockData.get("version")).intValue();
            int timestamp = ((Long) blockData.get("timestamp")).intValue();
            long previousBlock = Convert.parseUnsignedLong((String) blockData.get("previousBlock"));
            long totalAmountNQT = Convert.parseLong(blockData.get("totalAmountNQT"));
            long totalFeeNQT = Convert.parseLong(blockData.get("totalFeeNQT"));
            int payloadLength = ((Long) blockData.get("payloadLength")).intValue();
            byte[] payloadHash = Convert.parseHexString((String) blockData.get("payloadHash"));
            byte[] generatorPublicKey = Convert.parseHexString((String) blockData.get("generatorPublicKey"));
            byte[] generationSignature = Convert.parseHexString((String) blockData.get("generationSignature"));
            byte[] blockSignature = Convert.parseHexString((String) blockData.get("blockSignature"));
            byte[] previousBlockHash = version == 1 ? null : Convert.parseHexString((String) blockData.get("previousBlockHash"));
            List<TransactionImpl> blockTransactions = new ArrayList<>();
            for (Object transactionData : (JSONArray) blockData.get("transactions")) {
                blockTransactions.add(TransactionImpl.parseTransaction((JSONObject) transactionData));
            }
            EcBlockImpl block = new EcBlockImpl(version, timestamp, previousBlock, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash, generatorPublicKey,
                    generationSignature, blockSignature, previousBlockHash, blockTransactions);
            if (!block.checkSignature()) {
                throw new EcNotValidExceptionEc("Invalid block signature");
            }
            return block;
        } catch (EcNotValidExceptionEc | RuntimeException e) {
            LoggerUtil.logDebug("Failed to parse block: " + blockData.toJSONString());
            throw e;
        }
    }

    @Override
    public int getECVersion() {
        return version;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public long getPreviousBlockId() {
        return previousBlockId;
    }

    @Override
    public byte[] getFoundryPublicKey() {
        if (generatorPublicKey == null) {
            generatorPublicKey = Account.getPublicKey(generatorId);
        }
        return generatorPublicKey;
    }

    @Override
    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    @Override
    public long getTotalAmountNQT() {
        return totalAmountNQT;
    }

    @Override
    public long getTotalFeeNQT() {
        return totalFeeNQT;
    }

    @Override
    public int getPayloadLength() {
        return payloadLength;
    }

    @Override
    public byte[] getPayloadHash() {
        return payloadHash;
    }

    @Override
    public byte[] getFoundrySignature() {
        return generationSignature;
    }

    @Override
    public byte[] getBlockSignature() {
        return blockSignature;
    }

    @Override
    public List<TransactionImpl> getTransactions() {
        if (this.blockTransactions == null) {
            List<TransactionImpl> transactions = Collections.unmodifiableList(TransactionH2.selectBlockTransactions(getECId()));
            for (TransactionImpl transaction : transactions) {
                transaction.setBlock(this);
            }
            this.blockTransactions = transactions;
        }
        return this.blockTransactions;
    }

    @Override
    public long getBaseTarget() {
        return baseTarget;
    }

    @Override
    public BigInteger getCumulativeDifficulty() {
        return cumulativeDifficulty;
    }

    @Override
    public long getNextBlockId() {
        return nextBlockId;
    }

    void setNextBlockId(long nextBlockId) {
        this.nextBlockId = nextBlockId;
    }

    @Override
    public int getHeight() {
        if (height == -1) {
            throw new IllegalStateException("EcBlock height not yet set");
        }
        return height;
    }

    @Override
    public long getECId() {
        if (id == 0) {
            if (blockSignature == null) {
                throw new IllegalStateException("EcBlock is not signed yet");
            }
            byte[] hash = Crypto.sha256().digest(bytes());
            BigInteger bigInteger = new BigInteger(1, new byte[]{hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
            id = bigInteger.longValue();
            stringId = bigInteger.toString();
        }
        return id;
    }

    @Override
    public String getStringECId() {
        if (stringId == null) {
            getECId();
            if (stringId == null) {
                stringId = Long.toUnsignedString(id);
            }
        }
        return stringId;
    }

    @Override
    public long getFoundryId() {
        if (generatorId == 0) {
            generatorId = Account.getId(getFoundryPublicKey());
        }
        return generatorId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EcBlockImpl && this.getECId() == ((EcBlockImpl) o).getECId();
    }

    @Override
    public int hashCode() {
        return (int) (getECId() ^ (getECId() >>> 32));
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("timestamp", timestamp);
        json.put("previousBlock", Long.toUnsignedString(previousBlockId));
        json.put("totalAmountNQT", totalAmountNQT);
        json.put("totalFeeNQT", totalFeeNQT);
        json.put("payloadLength", payloadLength);
        json.put("payloadHash", Convert.toHexString(payloadHash));
        json.put("generatorPublicKey", Convert.toHexString(getFoundryPublicKey()));
        json.put("generationSignature", Convert.toHexString(generationSignature));
        if (version > 1) {
            json.put("previousBlockHash", Convert.toHexString(previousBlockHash));
        }
        json.put("blockSignature", Convert.toHexString(blockSignature));
        JSONArray transactionsData = new JSONArray();
        getTransactions().forEach(transaction -> transactionsData.add(transaction.getJSONObject()));
        json.put("transactions", transactionsData);
        return json;
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    byte[] bytes() {
        if (bytes == null) {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 8 + 4 + (version < 3 ? (4 + 4) : (8 + 8)) + 4 + 32 + 32 + (32 + 32) + (blockSignature != null ? 64 : 0));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(version);
            buffer.putInt(timestamp);
            buffer.putLong(previousBlockId);
            buffer.putInt(getTransactions().size());
            if (version < 3) {
                buffer.putInt((int) (totalAmountNQT / Constants.ONE_EC));
                buffer.putInt((int) (totalFeeNQT / Constants.ONE_EC));
            } else {
                buffer.putLong(totalAmountNQT);
                buffer.putLong(totalFeeNQT);
            }
            buffer.putInt(payloadLength);
            buffer.put(payloadHash);
            buffer.put(getFoundryPublicKey());
            buffer.put(generationSignature);
            if (version > 1) {
                buffer.put(previousBlockHash);
            }
            if (blockSignature != null) {
                buffer.put(blockSignature);
            }
            bytes = buffer.array();
        }
        return bytes;
    }

    boolean verifyBlockSignature() {
        return checkSignature() && Account.setOrVerify(getFoundryId(), getFoundryPublicKey());
    }

    private boolean checkSignature() {
        if (!hasValidSignature) {
            byte[] data = Arrays.copyOf(bytes(), bytes.length - 64);
            hasValidSignature = blockSignature != null && Crypto.ecVerify(blockSignature, data, getFoundryPublicKey(), version >= 3);
        }
        return hasValidSignature;
    }

    boolean verifyGenerationSignature() throws BlockOutOfOrderException {

        try {

            EcBlockImpl previousBlock = EcBlockchainImpl.getInstance().getBlock(getPreviousBlockId());
            if (previousBlock == null) {
                throw new BlockOutOfOrderException("Can't ecVerify signature because previous block is missing", this);
            }

            if (version == 1 && !Crypto.ecVerify(generationSignature, previousBlock.generationSignature, getFoundryPublicKey(), false)) {
                return false;
            }

            Account account = Account.getAccount(getFoundryId());
            long effectiveBalance = account == null ? 0 : account.getEffectiveBalanceEC();
            if (effectiveBalance <= 0) {
                return false;
            }

            MessageDigest digest = Crypto.sha256();
            byte[] generationSignatureHash;
            if (version == 1) {
                generationSignatureHash = digest.digest(generationSignature);
            } else {
                digest.update(previousBlock.generationSignature);
                generationSignatureHash = digest.digest(getFoundryPublicKey());
                if (!Arrays.equals(generationSignature, generationSignatureHash)) {
                    return false;
                }
            }

            BigInteger hit = new BigInteger(1, new byte[]{generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});

            return FoundryMachine.verifyHit(hit, BigInteger.valueOf(effectiveBalance), previousBlock, timestamp)
                    || (this.height < Constants.EC_TRANSPARENT_FORGING_BLOCK_2 && Arrays.binarySearch(badBlocks, this.getECId()) >= 0);

        } catch (RuntimeException e) {

            LoggerUtil.logError("Error verifying block generation signature", e);
            return false;

        }

    }

    void apply() {
        Account generatorAccount = Account.addOrGetAccount(getFoundryId());
        generatorAccount.apply(getFoundryPublicKey());
        long totalBackFees = 0;
        if (this.height > Constants.EC_SHUFFLING_BLOCK) {
            long[] backFees = new long[3];
            for (TransactionImpl transaction : getTransactions()) {
                long[] fees = transaction.getBackFees();
                for (int i = 0; i < fees.length; i++) {
                    backFees[i] += fees[i];
                }
            }
            for (int i = 0; i < backFees.length; i++) {
                if (backFees[i] == 0) {
                    break;
                }
                totalBackFees += backFees[i];
                Account previousGeneratorAccount = Account.getAccount(EcBlockH2.findBlockAtHeight(this.height - i - 1).getFoundryId());
                LoggerUtil.logInfo("Back fees " + ((double) backFees[i]) / Constants.ONE_EC + " EC to forger at height" + (this.height - i - 1));
                previousGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getECId(), backFees[i]);
                previousGeneratorAccount.addToForgedBalanceNQT(backFees[i]);
            }
        }
        if (totalBackFees != 0) {
            LoggerUtil.logInfo("Fee reduced by " + ((double) totalBackFees) / Constants.ONE_EC + " EC at height %d" + this.height);
        }
        generatorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getECId(), totalFeeNQT - totalBackFees);
        generatorAccount.addToForgedBalanceNQT(totalFeeNQT - totalBackFees);
    }

    void setPrevious(EcBlockImpl block) {
        if (block != null) {
            if (block.getECId() != getPreviousBlockId()) {
                // shouldn't happen as previous Id is already verified, but just in case
                throw new IllegalStateException("Previous block Id doesn't match");
            }
            this.height = block.getHeight() + 1;
            this.calculateBaseTarget(block);
        } else {
            this.height = 0;
        }
        short index = 0;
        for (TransactionImpl transaction : getTransactions()) {
            transaction.setBlock(this);
            transaction.setIndex(index++);
        }
    }

    void loadTransactions() {
        for (TransactionImpl transaction : getTransactions()) {
            transaction.bytes();
            transaction.getAppendages();
        }
    }

    private void calculateBaseTarget(EcBlockImpl previousBlock) {
        long prevBaseTarget = previousBlock.baseTarget;
        if (previousBlock.getHeight() < Constants.EC_SHUFFLING_BLOCK) {
            baseTarget = BigInteger.valueOf(prevBaseTarget)
                    .multiply(BigInteger.valueOf(this.timestamp - previousBlock.timestamp))
                    .divide(BigInteger.valueOf(60)).longValue();
            if (baseTarget < 0 || baseTarget > Constants.EC_MAX_BASE_TARGET) {
                baseTarget = Constants.EC_MAX_BASE_TARGET;
            }
            if (baseTarget < prevBaseTarget / 2) {
                baseTarget = prevBaseTarget / 2;
            }
            if (baseTarget == 0) {
                baseTarget = 1;
            }
            long twofoldCurBaseTarget = prevBaseTarget * 2;
            if (twofoldCurBaseTarget < 0) {
                twofoldCurBaseTarget = Constants.EC_MAX_BASE_TARGET;
            }
            if (baseTarget > twofoldCurBaseTarget) {
                baseTarget = twofoldCurBaseTarget;
            }
        } else if (previousBlock.getHeight() % 2 == 0) {
            EcBlockImpl block = EcBlockH2.findBlockAtHeight(previousBlock.getHeight() - 2);
            int blocktimeAverage = (this.timestamp - block.timestamp) / 3;
            if (blocktimeAverage > 60) {
                baseTarget = (prevBaseTarget * Math.min(blocktimeAverage, Constants.EC_MAX_BLOCKTIME_LIMIT)) / 60;
            } else {
                baseTarget = prevBaseTarget - prevBaseTarget * Constants.EC_BASE_TARGET_GAMMA
                        * (60 - Math.max(blocktimeAverage, Constants.EC_MIN_BLOCKTIME_LIMIT)) / 6000;
            }
            if (baseTarget < 0 || baseTarget > Constants.EC_MAX_BASE_TARGET_2) {
                baseTarget = Constants.EC_MAX_BASE_TARGET_2;
            }
            if (baseTarget < Constants.EC_MIN_BASE_TARGET) {
                baseTarget = Constants.EC_MIN_BASE_TARGET;
            }
        } else {
            baseTarget = prevBaseTarget;
        }
        cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(Convert.TWO_64.divide(BigInteger.valueOf(baseTarget)));
    }
}
