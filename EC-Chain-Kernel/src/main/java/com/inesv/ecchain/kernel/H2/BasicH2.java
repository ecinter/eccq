package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.deploy.RuntimeEnvironment;
import org.h2.jdbcx.JdbcConnectionPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class BasicH2 {
    @Autowired
    private static RuntimeEnvironment runtimeEnvironment;
    private String h2Url;
    private String h2Username;
    private String h2Password;
    private int maxConnections;
    private int loginTimeout;
    private int defaultLockTimeout;
    private int maxMemoryRows;
    private JdbcConnectionPool cp;
    private volatile int maxActiveConnections;
    private volatile boolean initialized = false;

    public BasicH2() {

    }

    public BasicH2(h2Properties h2Properties) {
        long maxCacheSize = h2Properties.maxCacheSize;
        if (maxCacheSize == 0) {
            maxCacheSize = Math.min(256, Math.max(16, (Runtime.getRuntime().maxMemory() / (1024 * 1024) - 128) / 2)) * 1024;
        }
        String dbUrl = h2Properties.dbUrl;
        if (dbUrl == null) {
            String dbDir = runtimeEnvironment.getDirProvider().getH2Dir(h2Properties.dbDir);
            dbUrl = String.format("jdbc:%s:%s;%s", h2Properties.dbType, dbDir, h2Properties.dbParams);
        }
        if (!dbUrl.contains("MV_STORE=")) {
            dbUrl += ";MV_STORE=FALSE";
        }
        if (!dbUrl.contains("CACHE_SIZE=")) {
            dbUrl += ";CACHE_SIZE=" + maxCacheSize;
        }
        this.h2Url = dbUrl;
        this.h2Username = h2Properties.dbUsername;
        this.h2Password = h2Properties.dbPassword;
        this.maxConnections = h2Properties.maxConnections;
        this.loginTimeout = h2Properties.loginTimeout;
        this.defaultLockTimeout = h2Properties.defaultLockTimeout;
        this.maxMemoryRows = h2Properties.maxMemoryRows;
    }


    public void init(H2Version h2Version) {
        LoggerUtil.logDebug("Database jdbc url set to " + h2Url + " username " + h2Username);
        FullTextTrigger.setActive(true);
        cp = JdbcConnectionPool.create(h2Url, h2Username, h2Password);
        cp.setMaxConnections(maxConnections);
        cp.setLoginTimeout(loginTimeout);
        try (Connection con = cp.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate("SET DEFAULT_LOCK_TIMEOUT " + defaultLockTimeout);
            stmt.executeUpdate("SET MAX_MEMORY_ROWS " + maxMemoryRows);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        h2Version.init(this);
        initialized = true;
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        try {
            FullTextTrigger.setActive(false);
            Connection con = cp.getConnection();
            Statement stmt = con.createStatement();
            stmt.execute("SHUTDOWN COMPACT");
            LoggerUtil.logInfo("Database shutdown completed");
        } catch (SQLException e) {
            LoggerUtil.logError(e.toString(), e);
        }
    }

    public void analyzeTables() {
        try (Connection con = cp.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.execute("ANALYZE SAMPLE_SIZE 0");
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection con = getPooledConnection();
        con.setAutoCommit(true);
        return con;
    }

    protected Connection getPooledConnection() throws SQLException {
        Connection con = cp.getConnection();
        int activeConnections = cp.getActiveConnections();
        if (activeConnections > maxActiveConnections) {
            maxActiveConnections = activeConnections;
            LoggerUtil.logDebug("Database connection pool current size: " + activeConnections);
        }
        return con;
    }

    public String geth2Url() {
        return h2Url;
    }

    public static final class h2Properties {

        private long maxCacheSize;
        private String dbUrl;
        private String dbType;
        private String dbDir;
        private String dbParams;
        private String dbUsername;
        private String dbPassword;
        private int maxConnections;
        private int loginTimeout;
        private int defaultLockTimeout;
        private int maxMemoryRows;

        public h2Properties maxCacheSize(int maxCacheSize) {
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public h2Properties dbUrl(String dbUrl) {
            this.dbUrl = dbUrl;
            return this;
        }

        public h2Properties dbType(String dbType) {
            this.dbType = dbType;
            return this;
        }

        public h2Properties dbDir(String dbDir) {
            this.dbDir = dbDir;
            return this;
        }

        public h2Properties dbParams(String dbParams) {
            this.dbParams = dbParams;
            return this;
        }

        public h2Properties dbUsername(String dbUsername) {
            this.dbUsername = dbUsername;
            return this;
        }

        public h2Properties dbPassword(String dbPassword) {
            this.dbPassword = dbPassword;
            return this;
        }

        public h2Properties maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public h2Properties loginTimeout(int loginTimeout) {
            this.loginTimeout = loginTimeout;
            return this;
        }

        public h2Properties defaultLockTimeout(int defaultLockTimeout) {
            this.defaultLockTimeout = defaultLockTimeout;
            return this;
        }

        public h2Properties maxMemoryRows(int maxMemoryRows) {
            this.maxMemoryRows = maxMemoryRows;
            return this;
        }

    }

}
