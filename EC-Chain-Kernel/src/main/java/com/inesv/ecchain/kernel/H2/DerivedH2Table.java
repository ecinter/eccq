package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import com.inesv.ecchain.kernel.core.H2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class DerivedH2Table {

    protected static final TransactionalH2 h2 = H2.H2;

    protected final String table;

    protected DerivedH2Table(String table) {
        this.table = table;
        EcBlockchainProcessorImpl.getInstance().registerDerivedTable(this);
    }

    public void rollback(int height) {
        if (!h2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = h2.getConnection();
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table + " WHERE height > ?")) {
            pstmtDelete.setInt(1, height);
            pstmtDelete.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public void truncate() {
        if (!h2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = h2.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE " + table);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public void trim(int height) {
        //nothing to trim
    }

    public void establishSearchIndex(Connection con) throws SQLException {
        //implemented in EntityH2Table only
    }

    public boolean isLasting() {
        return false;
    }

    @Override
    public final String toString() {
        return table;
    }

}
