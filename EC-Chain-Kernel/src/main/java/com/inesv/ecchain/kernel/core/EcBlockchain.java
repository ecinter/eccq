package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.H2.H2Iterator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public interface EcBlockchain {

    void readECLock();

    void readECUnlock();

    void updateECLock();

    void updateECUnlock();

    EcBlock getLastECBlock();

    EcBlock getLastECBlock(int timestamp);

    int getHeight();

    int getLastBlockTimestamp();

    EcBlock getBlock(long blockId);

    EcBlock getBlockAtHeight(int height);

    boolean hasBlock(long blockId);

    H2Iterator<? extends EcBlock> getBlocks(int from, int to);

    H2Iterator<? extends EcBlock> getBlocks(long accountId, int timestamp);

    H2Iterator<? extends EcBlock> getBlocks(long accountId, int timestamp, int from, int to);

    int getECBlockCount(long accountId);

    H2Iterator<? extends EcBlock> getBlocks(Connection con, PreparedStatement pstmt);

    List<Long> getBlockIdsAfter(long blockId, int limit);

    List<? extends EcBlock> getBlocksAfter(long blockId, int limit);

    List<? extends EcBlock> getBlocksAfter(long blockId, List<Long> blockList);

    long getBlockIdAtHeight(int height);

    EcBlock getECBlock(int timestamp);

    Transaction getTransaction(long transactionId);

    Transaction getTransactionByFullHash(String fullHash);

    int getTransactionCount();

    H2Iterator<? extends Transaction> getTransactions(long accountId, byte type, byte subtype, int blockTimestamp,
                                                      boolean includeExpiredPrunable);

    H2Iterator<? extends Transaction> getTransactions(long accountId, int numberOfConfirmations, byte type, byte subtype,
                                                      int blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly,
                                                      int from, int to, boolean includeExpiredPrunable, boolean executedOnly);

    H2Iterator<? extends Transaction> getTransactions(Connection con, PreparedStatement pstmt);

    List<? extends Transaction> getExpectedTransactions(Filter<Transaction> filter);

    H2Iterator<? extends Transaction> getReferencingTransactions(long transactionId, int from, int to);

}
