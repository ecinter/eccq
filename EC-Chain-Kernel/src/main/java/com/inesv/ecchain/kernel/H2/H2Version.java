package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.common.util.LoggerUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class H2Version {

    protected BasicH2 h2;

    void init(BasicH2 db) {
        this.h2 = db;
        Connection con = null;
        Statement stmt = null;
        try {
            con = db.getConnection();
            stmt = con.createStatement();
            int nextUpdate = 1;
            try {
                ResultSet rs = stmt.executeQuery("SELECT next_update FROM version");
                if (!rs.next()) {
                    throw new RuntimeException("Invalid version table");
                }
                nextUpdate = rs.getInt("next_update");
                if (!rs.isLast()) {
                    throw new RuntimeException("Invalid version table");
                }
                rs.close();
                LoggerUtil.logInfo("Database update may take a while if needed, current H2 version " + (nextUpdate - 1) + "...");
            } catch (SQLException e) {
                LoggerUtil.logInfo("Initializing an empty database");
                stmt.executeUpdate("CREATE TABLE version (next_update INT NOT NULL)");
                stmt.executeUpdate("INSERT INTO version VALUES (1)");
                con.commit();
            }
            update(nextUpdate);
        } catch (SQLException e) {
            H2Utils.rollback(con);
            throw new RuntimeException(e.toString(), e);
        } finally {
            H2Utils.h2close(stmt, con);
        }

    }

    protected void apply(String sql) {
        Connection con = null;
        Statement stmt = null;
        try {
            con = h2.getConnection();
            stmt = con.createStatement();
            try {
                if (sql != null) {
                    LoggerUtil.logDebug("Will apply sql:\n" + sql);
                    stmt.executeUpdate(sql);
                }
                stmt.executeUpdate("UPDATE version SET next_update = next_update + 1");
                con.commit();
            } catch (Exception e) {
                H2Utils.rollback(con);
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error executing " + sql, e);
        } finally {
            H2Utils.h2close(stmt, con);
        }
    }

    protected abstract void update(int nextUpdate);

}
