package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.common.util.ReadWriteManager;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class EcBlockchainImpl implements EcBlockchain {

    private static final EcBlockchainImpl instance = new EcBlockchainImpl();
    private final ReadWriteManager lock = new ReadWriteManager();
    private final AtomicReference<EcBlockImpl> lastBlock = new AtomicReference<>();

    private EcBlockchainImpl() {
    }

    public static EcBlockchainImpl getInstance() {
        return instance;
    }

    @Override
    public void readECLock() {
        lock.readLock().lock();
    }

    @Override
    public void readECUnlock() {
        lock.readLock().unlock();
    }

    @Override
    public void updateECLock() {
        lock.updateLock().lock();
    }

    @Override
    public void updateECUnlock() {
        lock.updateLock().unlock();
    }

    void writeLock() {
        lock.writeLock().lock();
    }

    void writeUnlock() {
        lock.writeLock().unlock();
    }

    @Override
    public EcBlockImpl getLastECBlock() {
        return lastBlock.get();
    }

    void setLastBlock(EcBlockImpl block) {
        lastBlock.set(block);
    }

    @Override
    public int getHeight() {
        EcBlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getHeight();
    }

    @Override
    public int getLastBlockTimestamp() {
        EcBlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getTimestamp();
    }

    @Override
    public EcBlockImpl getLastECBlock(int timestamp) {
        EcBlockImpl block = lastBlock.get();
        if (timestamp >= block.getTimestamp()) {
            return block;
        }
        return EcBlockH2.findLastBlock(timestamp);
    }

    @Override
    public EcBlockImpl getBlock(long blockId) {
        EcBlockImpl block = lastBlock.get();
        if (block.getECId() == blockId) {
            return block;
        }
        return EcBlockH2.findBlock(blockId);
    }

    @Override
    public boolean hasBlock(long blockId) {
        return lastBlock.get().getECId() == blockId || EcBlockH2.hasBlock(blockId);
    }

    @Override
    public H2Iterator<EcBlockImpl> getBlocks(int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height <= ? AND height >= ? ORDER BY height DESC");
            int blockchainHeight = getHeight();
            pstmt.setInt(1, blockchainHeight - from);
            pstmt.setInt(2, blockchainHeight - to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public H2Iterator<EcBlockImpl> getBlocks(long accountId, int timestamp) {
        return getBlocks(accountId, timestamp, 0, -1);
    }

    @Override
    public H2Iterator<EcBlockImpl> getBlocks(long accountId, int timestamp, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE generator_id = ? "
                    + (timestamp > 0 ? " AND timestamp >= ? " : " ") + "ORDER BY height DESC"
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            if (timestamp > 0) {
                pstmt.setInt(++i, timestamp);
            }
            H2Utils.setLimits(++i, pstmt, from, to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public int getECBlockCount(long accountId) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM block WHERE generator_id = ?")) {
            pstmt.setLong(1, accountId);
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public H2Iterator<EcBlockImpl> getBlocks(Connection con, PreparedStatement pstmt) {
        return new H2Iterator<>(con, pstmt, EcBlockH2::loadBlock);
    }

    @Override
    public List<Long> getBlockIdsAfter(long blockId, int limit) {
        // Check the block cache
        List<Long> result = new ArrayList<>(EcBlockH2.BLOCK_CACHE_SIZE);
        synchronized (EcBlockH2.blockCache) {
            EcBlockImpl block = EcBlockH2.blockCache.get(blockId);
            if (block != null) {
                Collection<EcBlockImpl> cacheMap = EcBlockH2.heightMap.tailMap(block.getHeight() + 1).values();
                for (EcBlockImpl cacheBlock : cacheMap) {
                    if (result.size() >= limit) {
                        break;
                    }
                    result.add(cacheBlock.getECId());
                }
                return result;
            }
        }
        // Search the database
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block "
                     + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                     + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("Id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public List<EcBlockImpl> getBlocksAfter(long blockId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        // Check the block cache
        List<EcBlockImpl> result = new ArrayList<>(EcBlockH2.BLOCK_CACHE_SIZE);
        synchronized (EcBlockH2.blockCache) {
            EcBlockImpl block = EcBlockH2.blockCache.get(blockId);
            if (block != null) {
                Collection<EcBlockImpl> cacheMap = EcBlockH2.heightMap.tailMap(block.getHeight() + 1).values();
                for (EcBlockImpl cacheBlock : cacheMap) {
                    if (result.size() >= limit) {
                        break;
                    }
                    result.add(cacheBlock);
                }
                return result;
            }
        }
        // Search the database
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                     + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                     + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(EcBlockH2.loadBlock(con, rs, true));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public List<EcBlockImpl> getBlocksAfter(long blockId, List<Long> blockList) {
        if (blockList.isEmpty()) {
            return Collections.emptyList();
        }
        // Check the block cache
        List<EcBlockImpl> result = new ArrayList<>(EcBlockH2.BLOCK_CACHE_SIZE);
        synchronized (EcBlockH2.blockCache) {
            EcBlockImpl block = EcBlockH2.blockCache.get(blockId);
            if (block != null) {
                Collection<EcBlockImpl> cacheMap = EcBlockH2.heightMap.tailMap(block.getHeight() + 1).values();
                int index = 0;
                for (EcBlockImpl cacheBlock : cacheMap) {
                    if (result.size() >= blockList.size() || cacheBlock.getECId() != blockList.get(index++)) {
                        break;
                    }
                    result.add(cacheBlock);
                }
                return result;
            }
        }
        // Search the database
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                     + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                     + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, blockList.size());
            try (ResultSet rs = pstmt.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    EcBlockImpl block = EcBlockH2.loadBlock(con, rs, true);
                    if (block.getECId() != blockList.get(index++)) {
                        break;
                    }
                    result.add(block);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public long getBlockIdAtHeight(int height) {
        EcBlock ecBlock = lastBlock.get();
        if (height > ecBlock.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current EC_BLOCKCHAIN is at " + ecBlock.getHeight());
        }
        if (height == ecBlock.getHeight()) {
            return ecBlock.getECId();
        }
        return EcBlockH2.findBlockIdAtHeight(height);
    }

    @Override
    public EcBlockImpl getBlockAtHeight(int height) {
        EcBlockImpl block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current EC_BLOCKCHAIN is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block;
        }
        return EcBlockH2.findBlockAtHeight(height);
    }

    @Override
    public EcBlockImpl getECBlock(int timestamp) {
        EcBlock ecBlock = getLastECBlock(timestamp);
        if (ecBlock == null) {
            return getBlockAtHeight(0);
        }
        return EcBlockH2.findBlockAtHeight(Math.max(ecBlock.getHeight() - 720, 0));
    }

    @Override
    public TransactionImpl getTransaction(long transactionId) {
        return TransactionH2.selectTransaction(transactionId);
    }

    @Override
    public TransactionImpl getTransactionByFullHash(String fullHash) {
        return TransactionH2.selectTransactionByFullHash(Convert.parseHexString(fullHash));
    }

    @Override
    public int getTransactionCount() {
        try (Connection con = H2.H2.getConnection(); PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM transaction");
             ResultSet rs = pstmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public H2Iterator<TransactionImpl> getTransactions(long accountId, byte type, byte subtype, int blockTimestamp,
                                                       boolean includeExpiredPrunable) {
        return getTransactions(accountId, 0, type, subtype, blockTimestamp, false, false, false, 0, -1, includeExpiredPrunable, false);
    }
    @Override
    public H2Iterator<TransactionImpl> getTransactions(long accountId, int numberOfConfirmations, byte type, byte subtype,
                                                       int blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly,
                                                       int from, int to, boolean includeExpiredPrunable, boolean executedOnly) {
        if (phasedOnly && nonPhasedOnly) {
            throw new IllegalArgumentException("At least one of phasedOnly or nonPhasedOnly must be false");
        }
        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        if (height < 0) {
            throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                    + " exceeds current EC_BLOCKCHAIN height " + getHeight());
        }
        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("SELECT transaction.* FROM transaction ");
            if (executedOnly && !nonPhasedOnly) {
                buf.append(" LEFT JOIN phasing_poll_result ON transaction.Id = phasing_poll_result.Id ");
            }
            buf.append("WHERE recipient_id = ? AND sender_id <> ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type >= 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND transaction.height <= ? ");
            }
            if (withMessage) {
                buf.append("AND (has_message = TRUE OR has_encrypted_message = TRUE ");
                buf.append("OR ((has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE) AND timestamp > ?)) ");
            }
            if (phasedOnly) {
                buf.append("AND phased = TRUE ");
            } else if (nonPhasedOnly) {
                buf.append("AND phased = FALSE ");
            }
            if (executedOnly && !nonPhasedOnly) {
                buf.append("AND (phased = FALSE OR approved = TRUE) ");
            }
            buf.append("UNION ALL SELECT transaction.* FROM transaction ");
            if (executedOnly && !nonPhasedOnly) {
                buf.append(" LEFT JOIN phasing_poll_result ON transaction.Id = phasing_poll_result.Id ");
            }
            buf.append("WHERE sender_id = ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type >= 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND transaction.height <= ? ");
            }
            if (withMessage) {
                buf.append("AND (has_message = TRUE OR has_encrypted_message = TRUE OR has_encrypttoself_message = TRUE ");
                buf.append("OR ((has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE) AND timestamp > ?)) ");
            }
            if (phasedOnly) {
                buf.append("AND phased = TRUE ");
            } else if (nonPhasedOnly) {
                buf.append("AND phased = FALSE ");
            }
            if (executedOnly && !nonPhasedOnly) {
                buf.append("AND (phased = FALSE OR approved = TRUE) ");
            }

            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            buf.append(H2Utils.limitsClause(from, to));
            con = H2.H2.getConnection();
            PreparedStatement pstmt;
            int i = 0;
            pstmt = con.prepareStatement(buf.toString());
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setInt(++i, blockTimestamp);
            }
            if (type >= 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            int prunableExpiration = Math.max(0, Constants.INCLUDE_EXPIRED_PRUNABLE && includeExpiredPrunable ?
                    new EcTime.EpochEcTime().getTime() - Constants.EC_MAX_PRUNABLE_LIFETIME :
                    new EcTime.EpochEcTime().getTime() - Constants.EC_MIN_PRUNABLE_LIFETIME);
            if (withMessage) {
                pstmt.setInt(++i, prunableExpiration);
            }
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setInt(++i, blockTimestamp);
            }
            if (type >= 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            if (withMessage) {
                pstmt.setInt(++i, prunableExpiration);
            }
            H2Utils.setLimits(++i, pstmt, from, to);
            return getTransactions(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }
    @Override
    public H2Iterator<TransactionImpl> getReferencingTransactions(long transactionId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* FROM transaction, referenced_transaction "
                    + "WHERE referenced_transaction.referenced_transaction_id = ? "
                    + "AND referenced_transaction.transaction_id = transaction.id "
                    + "ORDER BY transaction.block_timestamp DESC, transaction.transaction_index DESC "
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, transactionId);
            H2Utils.setLimits(++i, pstmt, from, to);
            return getTransactions(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public H2Iterator<TransactionImpl> getTransactions(Connection con, PreparedStatement pstmt) {
        return new H2Iterator<>(con, pstmt, TransactionH2::loadTransaction);
    }

    @Override
    public List<TransactionImpl> getExpectedTransactions(Filter<Transaction> filter) {
        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        EcBlockchainProcessorImpl blockchainProcessor = EcBlockchainProcessorImpl.getInstance();
        List<TransactionImpl> result = new ArrayList<>();
        readECLock();
        try {
            if (getHeight() >= Constants.EC_PHASING_BLOCK) {
                try (H2Iterator<TransactionImpl> phasedTransactions = PhasingPoll.getFinishingTransactions(getHeight() + 1)) {
                    for (TransactionImpl phasedTransaction : phasedTransactions) {
                        try {
                            phasedTransaction.validate();
                            if (!phasedTransaction.attachmentIsDuplicate(duplicates, false) && filter.ok(phasedTransaction)) {
                                result.add(phasedTransaction);
                            }
                        } catch (EcValidationException ignore) {
                        }
                    }
                }
            }
            blockchainProcessor.selectUnconfirmedTransactions(duplicates, getLastECBlock(), -1).forEach(
                    unconfirmedTransaction -> {
                        TransactionImpl transaction = unconfirmedTransaction.getTransaction();
                        if (transaction.getPhasing() == null && filter.ok(transaction)) {
                            result.add(transaction);
                        }
                    }
            );
        } finally {
            readECUnlock();
        }
        return result;
    }
}
