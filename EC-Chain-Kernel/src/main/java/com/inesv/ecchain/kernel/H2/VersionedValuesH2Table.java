package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.kernel.core.EcBlockchainImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public abstract class VersionedValuesH2Table<T, V> extends ValuesH2Table<T, V> {

    protected VersionedValuesH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        super(table, dbKeyFactory, true);
    }

    public final boolean delete(T t) {
        if (t == null) {
            return false;
        }
        if (!h2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        H2Key h2Key = dbKeyFactory.newKey(t);
        int height = EcBlockchainImpl.getInstance().getHeight();
        try (Connection con = h2.getConnection();
             PreparedStatement pstmtCount = con.prepareStatement("SELECT 1 FROM " + table + dbKeyFactory.getPKClause()
                     + " AND height < ? LIMIT 1")) {
            int i = h2Key.setH2KeyPK(pstmtCount);
            pstmtCount.setInt(i, height);
            try (ResultSet rs = pstmtCount.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + table
                            + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND height = ? AND latest = TRUE")) {
                        int j = h2Key.setH2KeyPK(pstmt);
                        pstmt.setInt(j, height);
                        if (pstmt.executeUpdate() > 0) {
                            return true;
                        }
                    }
                    List<V> values = get(h2Key);
                    if (values.isEmpty()) {
                        return false;
                    }
                    for (V v : values) {
                        save(con, t, v);
                    }
                    try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + table
                            + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE")) {
                        h2Key.setH2KeyPK(pstmt);
                        if (pstmt.executeUpdate() == 0) {
                            throw new RuntimeException(); // should not happen
                        }
                    }
                    return true;
                } else {
                    try (PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table + dbKeyFactory.getPKClause())) {
                        h2Key.setH2KeyPK(pstmtDelete);
                        return pstmtDelete.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            h2.getCache(table).remove(h2Key);
        }
    }

}
