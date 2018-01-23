package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.annotation.PostConstruct;
import java.io.*;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;

public final class FxtDistribution implements Listener<EcBlock> {

    @PostConstruct
    public static void initPostConstruct() {
        EcBlockchainProcessorImpl.getInstance().addECListener(new FxtDistribution(), EcBlockchainProcessorEvent.AFTER_BLOCK_ACCEPT);
    }

    public static void start() {
    }

    @Override
    public void notify(EcBlock ecBlock) {

        final int currentHeight = ecBlock.getHeight();
        if (Constants.hasSnapshot) {
            if (currentHeight == Constants.DISTRIBUTION_END) {
                LoggerUtil.logDebug("Distributing FXT based on snapshot file " + Constants.fxtJsonFile);
                JSONObject snapshotJSON;
                try (Reader reader = new BufferedReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream(Constants.fxtJsonFile)))) {
                    snapshotJSON = (JSONObject) JSONValue.parse(reader);
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                boolean wasInTransaction = H2.H2.isInTransaction();
                if (!wasInTransaction) {
                    H2.H2.beginTransaction();
                }
                try {
                    long initialQuantity = Property.getAsset(Constants.FXT_ASSET_ID).getInitialQuantityQNT();
                    Account issuerAccount = Account.getAccount(Constants.FXT_ISSUER_ID);
                    issuerAccount.addToAssetAndUnconfirmedAssetBalanceQNT(null, ecBlock.getECId(),
                            Constants.FXT_ASSET_ID, -initialQuantity);
                    long totalDistributed = 0;
                    Iterator<Map.Entry> iterator = snapshotJSON.entrySet().iterator();
                    int count = 0;
                    while (iterator.hasNext()) {
                        Map.Entry entry = iterator.next();
                        long accountId = Long.parseUnsignedLong((String) entry.getKey());
                        long quantity = (Long) entry.getValue();
                        Account.getAccount(accountId).addToAssetAndUnconfirmedAssetBalanceQNT(null, ecBlock.getECId(),
                                Constants.FXT_ASSET_ID, quantity);
                        totalDistributed += quantity;
                        if (++count % 1000 == 0) {
                            H2.H2.commitTransaction();
                        }
                    }
                    long excessFxtQuantity = initialQuantity - totalDistributed;
                    Property.deleteProperty(TransactionH2.selectTransaction(Constants.FXT_ASSET_ID), Constants.FXT_ASSET_ID, excessFxtQuantity);
                    LoggerUtil.logDebug("Deleted " + excessFxtQuantity + " excess QNT");
                    LoggerUtil.logDebug("Distributed " + totalDistributed + " QNT to " + count + " accounts");
                    H2.H2.commitTransaction();
                } catch (Exception e) {
                    H2.H2.rollbackTransaction();
                    throw new RuntimeException(e.toString(), e);
                } finally {
                    if (!wasInTransaction) {
                        H2.H2.endTransaction();
                    }
                }
            }
            return;
        }
        if (currentHeight <= Constants.DISTRIBUTION_START || currentHeight > Constants.DISTRIBUTION_END || (currentHeight - Constants.DISTRIBUTION_START) % Constants.DISTRIBUTION_FREQUENCY != 0) {
            return;
        }
        LoggerUtil.logDebug("Running FXT balance update at height " + currentHeight);
        Map<Long, BigInteger> accountBalanceTotals = new HashMap<>();
        for (int height = currentHeight - Constants.DISTRIBUTION_FREQUENCY + Constants.DISTRIBUTION_STEP; height <= currentHeight; height += Constants.DISTRIBUTION_STEP) {
            LoggerUtil.logDebug("Calculating balances at height " + height);
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmtCreate = con.prepareStatement("CREATE TEMP TABLE account_tmp NOT PERSISTENT AS SELECT id, MAX(height) as height FROM account "
                         + "WHERE height <= ? GROUP BY id")) {
                pstmtCreate.setInt(1, height);
                pstmtCreate.executeUpdate();
                try (PreparedStatement pstmtSelect = con.prepareStatement("SELECT account.id, account.balance FROM account, account_tmp WHERE account.id = account_tmp.id "
                        + "AND account.height = account_tmp.height AND account.balance > 0");
                     PreparedStatement pstmtDrop = con.prepareStatement("DROP TABLE account_tmp")) {
                    try (ResultSet rs = pstmtSelect.executeQuery()) {
                        while (rs.next()) {
                            Long accountId = rs.getLong("Id");
                            long balance = rs.getLong("balance");
                            if (Constants.logAccountId != 0) {
                                if (accountId == Constants.logAccountId) {
                                    LoggerUtil.logInfo("EC balance for " + Constants.logAccount + " at height " + height + ":\t" + balance);
                                }
                            }
                            BigInteger accountBalanceTotal = accountBalanceTotals.get(accountId);
                            accountBalanceTotals.put(accountId, accountBalanceTotal == null ?
                                    BigInteger.valueOf(balance) : accountBalanceTotal.add(BigInteger.valueOf(balance)));
                        }
                    } finally {
                        pstmtDrop.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
        LoggerUtil.logDebug("Updating balances for " + accountBalanceTotals.size() + " accounts");
        boolean wasInTransaction = H2.H2.isInTransaction();
        if (!wasInTransaction) {
            H2.H2.beginTransaction();
        }
        H2.H2.clearCache();
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT balance FROM account_fxt WHERE id = ? ORDER BY height DESC LIMIT 1");
             PreparedStatement pstmtInsert = con.prepareStatement("INSERT INTO account_fxt (id, balance, height) values (?, ?, ?)")) {
            int count = 0;
            for (Map.Entry<Long, BigInteger> entry : accountBalanceTotals.entrySet()) {
                long accountId = entry.getKey();
                BigInteger balanceTotal = entry.getValue();
                pstmtSelect.setLong(1, accountId);
                try (ResultSet rs = pstmtSelect.executeQuery()) {
                    if (rs.next()) {
                        balanceTotal = balanceTotal.add(new BigInteger(rs.getBytes("balance")));
                    }
                }
                if (Constants.logAccountId != 0) {
                    if (accountId == Constants.logAccountId) {
                        LoggerUtil.logInfo("Average EC balance for " + Constants.logAccount + " as of height " + currentHeight + ":\t"
                                + balanceTotal.divide(BigInteger.valueOf((currentHeight - Constants.DISTRIBUTION_START) / Constants.DISTRIBUTION_STEP)).longValueExact());
                    }
                }
                pstmtInsert.setLong(1, accountId);
                pstmtInsert.setBytes(2, balanceTotal.toByteArray());
                pstmtInsert.setInt(3, currentHeight);
                pstmtInsert.executeUpdate();
                if (++count % 1000 == 0) {
                    H2.H2.commitTransaction();
                }
            }
            accountBalanceTotals.clear();
            H2.H2.commitTransaction();
            if (currentHeight == Constants.DISTRIBUTION_END) {
                LoggerUtil.logDebug("Running FXT distribution at height " + currentHeight);
                long totalDistributed = 0;
                count = 0;
                SortedMap<String, Long> snapshotMap = new TreeMap<>();
                try (PreparedStatement pstmtCreate = con.prepareStatement("CREATE TEMP TABLE account_fxt_tmp NOT PERSISTENT AS SELECT id, MAX(height) AS height FROM account_fxt "
                        + "WHERE height <= ? GROUP BY id");
                     PreparedStatement pstmtDrop = con.prepareStatement("DROP TABLE account_fxt_tmp")) {
                    pstmtCreate.setInt(1, currentHeight);
                    pstmtCreate.executeUpdate();
                    try (PreparedStatement pstmt = con.prepareStatement("SELECT account_fxt.id, account_fxt.balance FROM account_fxt, account_fxt_tmp "
                            + "WHERE account_fxt.id = account_fxt_tmp.id AND account_fxt.height = account_fxt_tmp.height");
                         ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            long accountId = rs.getLong("Id");
                            // 1 EC held for the full period should give 1 asset unit, i.e. 10000 QNT assuming 4 decimals
                            long quantity = new BigInteger(rs.getBytes("balance")).divide(Constants.BALANCE_DIVIDER).longValueExact();
                            if (Constants.logAccountId != 0) {
                                if (accountId == Constants.logAccountId) {
                                    LoggerUtil.logInfo("FXT quantity for " + Constants.logAccount + ":\t" + quantity);
                                }
                            }
                            Account.getAccount(accountId).addToAssetAndUnconfirmedAssetBalanceQNT(null, ecBlock.getECId(),
                                    Constants.FXT_ASSET_ID, quantity);
                            snapshotMap.put(Long.toUnsignedString(accountId), quantity);
                            totalDistributed += quantity;
                            if (++count % 1000 == 0) {
                                H2.H2.commitTransaction();
                            }
                        }
                    } finally {
                        pstmtDrop.executeUpdate();
                    }
                }
                Account issuerAccount = Account.getAccount(Constants.FXT_ISSUER_ID);
                issuerAccount.addToAssetAndUnconfirmedAssetBalanceQNT(null, ecBlock.getECId(),
                        Constants.FXT_ASSET_ID, -totalDistributed);
                long excessFxtQuantity = Property.getAsset(Constants.FXT_ASSET_ID).getInitialQuantityQNT() - totalDistributed;
                issuerAccount.addToAssetAndUnconfirmedAssetBalanceQNT(null, ecBlock.getECId(),
                        Constants.FXT_ASSET_ID, -excessFxtQuantity);
                long issuerAssetBalance = issuerAccount.getPropertyBalanceQNT(Constants.FXT_ASSET_ID);
                if (issuerAssetBalance > 0) {
                    snapshotMap.put(Long.toUnsignedString(Constants.FXT_ISSUER_ID), issuerAssetBalance);
                } else {
                    snapshotMap.remove(Long.toUnsignedString(Constants.FXT_ISSUER_ID));
                }
                Property.deleteProperty(TransactionH2.selectTransaction(Constants.FXT_ASSET_ID), Constants.FXT_ASSET_ID, excessFxtQuantity);
                LoggerUtil.logDebug("Deleted " + excessFxtQuantity + " excess QNT");
                LoggerUtil.logDebug("Distributed " + totalDistributed + " QNT to " + count + " accounts");
                try (PrintWriter writer = new PrintWriter((new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Constants.fxtJsonFile)))), true)) {
                    StringBuilder sb = new StringBuilder(1024);
                    JSON.encodeObject(snapshotMap, sb);
                    writer.write(sb.toString());
                }
                H2.H2.commitTransaction();
            }
        } catch (Exception e) {
            H2.H2.rollbackTransaction();
            throw new RuntimeException(e.toString(), e);
        } finally {
            if (!wasInTransaction) {
                H2.H2.endTransaction();
            }
        }
    }
}
