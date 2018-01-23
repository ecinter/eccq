package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;

import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TransactionalH2 extends BasicH2 {

    private static final DbFactory factory = new DbFactory();
    private final ThreadLocal<DbConnection> localConnection = new ThreadLocal<>();
    private final ThreadLocal<Map<String, Map<H2Key, Object>>> transactionCaches = new ThreadLocal<>();
    private final ThreadLocal<Set<TransactionCallback>> transactionCallback = new ThreadLocal<>();
    private volatile long txTimes = 0;
    private volatile long txCount = 0;
    private volatile long statsTime = 0;

    public TransactionalH2(h2Properties h2Properties) {
        super(h2Properties);
    }

    private static void logThreshold(String msg) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(msg).append('\n');
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean firstLine = true;
        for (int i = 3; i < stackTrace.length; i++) {
            String line = stackTrace[i].toString();
            if (!line.startsWith("ec.")) {
                break;
            }
            if (firstLine) {
                firstLine = false;
            } else {
                sb.append('\n');
            }
            sb.append("  ").append(line);
        }
        LoggerUtil.logDebug(sb.toString());
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection con = localConnection.get();
        if (con != null) {
            return con;
        }
        return new DbConnection(super.getConnection());
    }

    public boolean isInTransaction() {
        return localConnection.get() != null;
    }

    public Connection beginTransaction() {
        if (localConnection.get() != null) {
            throw new IllegalStateException("Transaction already in progress");
        }
        try {
            Connection con = getPooledConnection();
            con.setAutoCommit(false);
            con = new DbConnection(con);
            ((DbConnection) con).txStart = System.currentTimeMillis();
            localConnection.set((DbConnection) con);
            transactionCaches.set(new HashMap<>());
            return con;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public void commitTransaction() {
        DbConnection con = localConnection.get();
        if (con == null) {
            throw new IllegalStateException("Not in transaction");
        }
        try {
            con.doCommit();
            Set<TransactionCallback> callbacks = transactionCallback.get();
            if (callbacks != null) {
                callbacks.forEach(TransactionCallback::commit);
                transactionCallback.set(null);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public void rollbackTransaction() {
        DbConnection con = localConnection.get();
        if (con == null) {
            throw new IllegalStateException("Not in transaction");
        }
        try {
            con.doRollback();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            transactionCaches.get().clear();
            Set<TransactionCallback> callbacks = transactionCallback.get();
            if (callbacks != null) {
                callbacks.forEach(TransactionCallback::rollback);
                transactionCallback.set(null);
            }
        }
    }

    public void endTransaction() {
        Connection con = localConnection.get();
        if (con == null) {
            throw new IllegalStateException("Not in transaction");
        }
        localConnection.set(null);
        transactionCaches.set(null);
        long now = System.currentTimeMillis();
        long elapsed = now - ((DbConnection) con).txStart;
        if (elapsed >= Constants.TX_THRESHOLD) {
            logThreshold(String.format("Database transaction required %.3f seconds at height %d",
                    (double) elapsed / 1000.0, EcBlockchainImpl.getInstance().getHeight()));
        } else {
            long count, times;
            boolean logStats = false;
            synchronized (this) {
                count = ++txCount;
                times = txTimes += elapsed;
                if (now - statsTime >= Constants.TX_INTERVAL) {
                    logStats = true;
                    txCount = 0;
                    txTimes = 0;
                    statsTime = now;
                }
            }
            if (logStats)
                LoggerUtil.logDebug(String.format("Average database transaction time is %.3f seconds",
                        (double) times / 1000.0 / (double) count));
        }
        H2Utils.h2close(con);
    }

    public void registerCallback(TransactionCallback callback) {
        Set<TransactionCallback> callbacks = transactionCallback.get();
        if (callbacks == null) {
            callbacks = new HashSet<>();
            transactionCallback.set(callbacks);
        }
        callbacks.add(callback);
    }

    Map<H2Key, Object> getCache(String tableName) {
        if (!isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        Map<H2Key, Object> cacheMap = transactionCaches.get().get(tableName);
        if (cacheMap == null) {
            cacheMap = new HashMap<>();
            transactionCaches.get().put(tableName, cacheMap);
        }
        return cacheMap;
    }

    void clearCache(String tableName) {
        Map<H2Key, Object> cacheMap = transactionCaches.get().get(tableName);
        if (cacheMap != null) {
            cacheMap.clear();
        }
    }

    public void clearCache() {
        transactionCaches.get().values().forEach(Map::clear);
    }

    private static final class DbStatement extends FilteredStatement {

        private DbStatement(Statement stmt) {
            super(stmt);
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            boolean b = super.execute(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > Constants.STMT_THRESHOLD)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                        (double) elapsed / 1000.0, EcBlockchainImpl.getInstance().getHeight(), sql));
            return b;
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            ResultSet r = super.executeQuery(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > Constants.STMT_THRESHOLD)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                        (double) elapsed / 1000.0, EcBlockchainImpl.getInstance().getHeight(), sql));
            return r;
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            int c = super.executeUpdate(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > Constants.STMT_THRESHOLD)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                        (double) elapsed / 1000.0, EcBlockchainImpl.getInstance().getHeight(), sql));
            return c;
        }
    }

    private static final class DbPreparedStatement extends FilteredPreparedStatement {
        private DbPreparedStatement(PreparedStatement stmt, String sql) {
            super(stmt, sql);
        }

        @Override
        public boolean execute() throws SQLException {
            long start = System.currentTimeMillis();
            boolean b = super.execute();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > Constants.STMT_THRESHOLD)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                        (double) elapsed / 1000.0, EcBlockchainImpl.getInstance().getHeight(), getSQL()));
            return b;
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            long start = System.currentTimeMillis();
            ResultSet r = super.executeQuery();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > Constants.STMT_THRESHOLD)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                        (double) elapsed / 1000.0, EcBlockchainImpl.getInstance().getHeight(), getSQL()));
            return r;
        }

        @Override
        public int executeUpdate() throws SQLException {
            long start = System.currentTimeMillis();
            int c = super.executeUpdate();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > Constants.STMT_THRESHOLD)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                        (double) elapsed / 1000.0, EcBlockchainImpl.getInstance().getHeight(), getSQL()));
            return c;
        }
    }

    private static final class DbFactory implements FilteredFactory {

        @Override
        public Statement establishStatement(Statement stmt) {
            return new DbStatement(stmt);
        }

        @Override
        public PreparedStatement establishPreparedStatement(PreparedStatement stmt, String sql) {
            return new DbPreparedStatement(stmt, sql);
        }
    }

    private final class DbConnection extends FilteredConnection {

        long txStart = 0;

        private DbConnection(Connection con) {
            super(con, factory);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            throw new UnsupportedOperationException("Use Db.beginTransaction() to start a new transaction");
        }

        @Override
        public void commit() throws SQLException {
            if (localConnection.get() == null) {
                super.commit();
            } else if (this != localConnection.get()) {
                throw new IllegalStateException("Previous connection not committed");
            } else {
                commitTransaction();
            }
        }

        private void doCommit() throws SQLException {
            super.commit();
        }

        @Override
        public void rollback() throws SQLException {
            if (localConnection.get() == null) {
                super.rollback();
            } else if (this != localConnection.get()) {
                throw new IllegalStateException("Previous connection not committed");
            } else {
                rollbackTransaction();
            }
        }

        private void doRollback() throws SQLException {
            super.rollback();
        }

        @Override
        public void close() throws SQLException {
            if (localConnection.get() == null) {
                super.close();
            } else if (this != localConnection.get()) {
                throw new IllegalStateException("Previous connection not committed");
            }
        }
    }
}
