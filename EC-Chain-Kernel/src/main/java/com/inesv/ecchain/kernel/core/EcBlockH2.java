package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.H2.H2Utils;

import java.math.BigInteger;
import java.sql.*;
import java.util.*;

final class EcBlockH2 {

    static final int BLOCK_CACHE_SIZE = 10;
    static final Map<Long, EcBlockImpl> blockCache = new HashMap<>();
    static final SortedMap<Integer, EcBlockImpl> heightMap = new TreeMap<>();
    static final Map<Long, TransactionImpl> transactionCache = new HashMap<>();
    static final EcBlockchain EC_BLOCKCHAIN = EcBlockchainImpl.getInstance();

    static {
        EcBlockchainProcessorImpl.getInstance().addECListener((block) -> {
            synchronized (blockCache) {
                int height = block.getHeight();
                Iterator<EcBlockImpl> it = blockCache.values().iterator();
                while (it.hasNext()) {
                    EcBlock cacheEcBlock = it.next();
                    int cacheHeight = cacheEcBlock.getHeight();
                    if (cacheHeight <= height - BLOCK_CACHE_SIZE || cacheHeight >= height) {
                        cacheEcBlock.getTransactions().forEach((tx) -> transactionCache.remove(tx.getTransactionId()));
                        heightMap.remove(cacheHeight);
                        it.remove();
                    }
                }
                block.getTransactions().forEach((tx) -> transactionCache.put(tx.getTransactionId(), (TransactionImpl) tx));
                heightMap.put(height, (EcBlockImpl) block);
                blockCache.put(block.getECId(), (EcBlockImpl) block);
            }
        }, EcBlockchainProcessorEvent.BLOCK_PUSHED);
    }

    static private void clearBlockCache() {
        synchronized (blockCache) {
            blockCache.clear();
            heightMap.clear();
            transactionCache.clear();
        }
    }

    static EcBlockImpl findBlock(long blockId) {
        // Check the block cache
        synchronized (blockCache) {
            EcBlockImpl block = blockCache.get(blockId);
            if (block != null) {
                return block;
            }
        }
        // Search the database
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE id = ?")) {
            pstmt.setLong(1, blockId);
            try (ResultSet rs = pstmt.executeQuery()) {
                EcBlockImpl block = null;
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
                return block;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static boolean hasBlock(long blockId) {
        return hasBlock(blockId, Integer.MAX_VALUE);
    }

    static boolean hasBlock(long blockId, int height) {
        // Check the block cache
        synchronized (blockCache) {
            EcBlockImpl block = blockCache.get(blockId);
            if (block != null) {
                return block.getHeight() <= height;
            }
        }
        // Search the database
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT height FROM block WHERE id = ?")) {
            pstmt.setLong(1, blockId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt("height") <= height;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static long findBlockIdAtHeight(int height) {
        // Check the cache
        synchronized (blockCache) {
            EcBlockImpl block = heightMap.get(height);
            if (block != null) {
                return block.getECId();
            }
        }
        // Search the database
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("EcBlock at height " + height + " not found in database!");
                }
                return rs.getLong("Id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static EcBlockImpl findBlockAtHeight(int height) {
        // Check the cache
        synchronized (blockCache) {
            EcBlockImpl block = heightMap.get(height);
            if (block != null) {
                return block;
            }
        }
        // Search the database
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height = ?")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                EcBlockImpl block;
                if (rs.next()) {
                    block = loadBlock(con, rs);
                } else {
                    throw new RuntimeException("EcBlock at height " + height + " not found in database!");
                }
                return block;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static EcBlockImpl findLastBlock() {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block ORDER BY timestamp DESC LIMIT 1")) {
            EcBlockImpl block = null;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
            }
            return block;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static EcBlockImpl findLastBlock(int timestamp) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE timestamp <= ? ORDER BY timestamp DESC LIMIT 1")) {
            pstmt.setInt(1, timestamp);
            EcBlockImpl block = null;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
            }
            return block;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static Set<Long> getBlockGenerators(int startHeight) {
        Set<Long> generators = new HashSet<>();
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement(
                     "SELECT generator_id, COUNT(generator_id) AS count FROM block WHERE height >= ? GROUP BY generator_id")) {
            pstmt.setInt(1, startHeight);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("count") > 1) {
                        generators.add(rs.getLong("generator_id"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return generators;
    }

    static EcBlockImpl loadBlock(Connection con, ResultSet rs) {
        return loadBlock(con, rs, false);
    }

    static EcBlockImpl loadBlock(Connection con, ResultSet rs, boolean loadTransactions) {
        try {
            int version = rs.getInt("version");
            int timestamp = rs.getInt("timestamp");
            long previousBlockId = rs.getLong("previous_block_id");
            long totalAmountNQT = rs.getLong("total_amount");
            long totalFeeNQT = rs.getLong("total_fee");
            int payloadLength = rs.getInt("payload_length");
            long generatorId = rs.getLong("generator_id");
            byte[] previousBlockHash = rs.getBytes("previous_block_hash");
            BigInteger cumulativeDifficulty = new BigInteger(rs.getBytes("cumulative_difficulty"));
            long baseTarget = rs.getLong("base_target");
            long nextBlockId = rs.getLong("next_block_id");
            int height = rs.getInt("height");
            byte[] generationSignature = rs.getBytes("generation_signature");
            byte[] blockSignature = rs.getBytes("block_signature");
            byte[] payloadHash = rs.getBytes("payload_hash");
            long id = rs.getLong("Id");
            return new EcBlockImpl(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                    generatorId, generationSignature, blockSignature, previousBlockHash,
                    cumulativeDifficulty, baseTarget, nextBlockId, height, id, loadTransactions ? TransactionH2.selectBlockTransactions(con, id) : null);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void saveBlock(Connection con, EcBlockImpl block) {
        try {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO block (id, version, timestamp, previous_block_id, "
                    + "total_amount, total_fee, payload_length, previous_block_hash, cumulative_difficulty, "
                    + "base_target, height, generation_signature, block_signature, payload_hash, generator_id) "
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, block.getECId());
                pstmt.setInt(++i, block.getECVersion());
                pstmt.setInt(++i, block.getTimestamp());
                H2Utils.h2setLongZeroToNull(pstmt, ++i, block.getPreviousBlockId());
                pstmt.setLong(++i, block.getTotalAmountNQT());
                pstmt.setLong(++i, block.getTotalFeeNQT());
                pstmt.setInt(++i, block.getPayloadLength());
                pstmt.setBytes(++i, block.getPreviousBlockHash());
                pstmt.setBytes(++i, block.getCumulativeDifficulty().toByteArray());
                pstmt.setLong(++i, block.getBaseTarget());
                pstmt.setInt(++i, block.getHeight());
                pstmt.setBytes(++i, block.getFoundrySignature());
                pstmt.setBytes(++i, block.getBlockSignature());
                pstmt.setBytes(++i, block.getPayloadHash());
                pstmt.setLong(++i, block.getFoundryId());
                pstmt.executeUpdate();
                TransactionH2.saveTransactions(con, block.getTransactions());
            }
            if (block.getPreviousBlockId() != 0) {
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE block SET next_block_id = ? WHERE id = ?")) {
                    pstmt.setLong(1, block.getECId());
                    pstmt.setLong(2, block.getPreviousBlockId());
                    pstmt.executeUpdate();
                }
                EcBlockImpl previousBlock;
                synchronized (blockCache) {
                    previousBlock = blockCache.get(block.getPreviousBlockId());
                }
                if (previousBlock != null) {
                    previousBlock.setNextBlockId(block.getECId());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void deleteBlocksFromHeight(int height) {
        long blockId;
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                blockId = rs.getLong("Id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        LoggerUtil.logInfo("Deleting blocks starting from height " + height);
        EcBlockH2.deleteBlocksFrom(blockId);
    }

    static EcBlockImpl deleteBlocksFrom(long blockId) {
        if (!H2.H2.isInTransaction()) {
            EcBlockImpl lastBlock;
            try {
                H2.H2.beginTransaction();
                lastBlock = deleteBlocksFrom(blockId);
                H2.H2.commitTransaction();
            } catch (Exception e) {
                H2.H2.rollbackTransaction();
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
            return lastBlock;
        }
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT db_id FROM block WHERE timestamp >= "
                     + "IFNULL ((SELECT timestamp FROM block WHERE id = ?), " + Integer.MAX_VALUE + ") ORDER BY timestamp DESC");
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM block WHERE db_id = ?")) {
            try {
                pstmtSelect.setLong(1, blockId);
                try (ResultSet rs = pstmtSelect.executeQuery()) {
                    H2.H2.commitTransaction();
                    while (rs.next()) {
                        pstmtDelete.setLong(1, rs.getLong("db_id"));
                        pstmtDelete.executeUpdate();
                        H2.H2.commitTransaction();
                    }
                }
                EcBlockImpl lastBlock = findLastBlock();
                lastBlock.setNextBlockId(0);
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE block SET next_block_id = NULL WHERE id = ?")) {
                    pstmt.setLong(1, lastBlock.getECId());
                    pstmt.executeUpdate();
                }
                H2.H2.commitTransaction();
                return lastBlock;
            } catch (SQLException e) {
                H2.H2.rollbackTransaction();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            clearBlockCache();
        }
    }

    static void deleteAll() {
        if (!H2.H2.isInTransaction()) {
            try {
                H2.H2.beginTransaction();
                deleteAll();
                H2.H2.commitTransaction();
            } catch (Exception e) {
                H2.H2.rollbackTransaction();
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
            return;
        }
        LoggerUtil.logInfo("Deleting EC_BLOCKCHAIN...");
        try (Connection con = H2.H2.getConnection();
             Statement stmt = con.createStatement()) {
            try {
                stmt.executeUpdate("SET REFERENTIAL_INTEGRITY FALSE");
                stmt.executeUpdate("TRUNCATE TABLE transaction");
                stmt.executeUpdate("TRUNCATE TABLE block");
                EcBlockchainProcessorImpl.getInstance().getDerivedTables().forEach(table -> {
                    if (table.isLasting()) {
                        try {
                            stmt.executeUpdate("TRUNCATE TABLE " + table.toString());
                        } catch (SQLException ignore) {
                        }
                    }
                });
                stmt.executeUpdate("SET REFERENTIAL_INTEGRITY TRUE");
                H2.H2.commitTransaction();
            } catch (SQLException e) {
                H2.H2.rollbackTransaction();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            clearBlockCache();
        }
    }

}
