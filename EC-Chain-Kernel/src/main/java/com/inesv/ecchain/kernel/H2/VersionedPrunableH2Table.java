package com.inesv.ecchain.kernel.H2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class VersionedPrunableH2Table<T> extends PrunableH2Table<T> {

    protected VersionedPrunableH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        super(table, dbKeyFactory, true, null);
    }

    protected VersionedPrunableH2Table(String table, H2KeyFactory<T> dbKeyFactory, String fullTextSearchColumns) {
        super(table, dbKeyFactory, true, fullTextSearchColumns);
    }

    public final boolean delete(T t) {
        throw new UnsupportedOperationException("Versioned prunable tables cannot support delete");
    }

    @Override
    public final void rollback(int height) {
        if (!h2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = h2.getConnection();
             PreparedStatement pstmtSetLatest = con.prepareStatement("UPDATE " + table
                     + " AS a SET a.latest = TRUE WHERE a.latest = FALSE AND a.height = "
                     + " (SELECT MAX(height) FROM " + table + " AS b WHERE " + dbKeyFactory.getSelfJoinClause() + ")")) {
            pstmtSetLatest.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

}
