package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.kernel.core.EcBlockchainImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class VersionedEntityH2Table<T> extends EntityH2Table<T> {

    protected VersionedEntityH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        super(table, dbKeyFactory, true, null);
    }

    protected VersionedEntityH2Table(String table, H2KeyFactory<T> dbKeyFactory, String fullTextSearchColumns) {
        super(table, dbKeyFactory, true, fullTextSearchColumns);
    }

    static void rollback(final TransactionalH2 db, final String table, final int height, final H2KeyFactory dbKeyFactory) {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = db.getConnection();
             PreparedStatement pstmtSelectToDelete = con.prepareStatement("SELECT DISTINCT " + dbKeyFactory.getPKColumns()
                     + " FROM " + table + " WHERE height > ?");
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table
                     + " WHERE height > ?");
             PreparedStatement pstmtSetLatest = con.prepareStatement("UPDATE " + table
                     + " SET latest = TRUE " + dbKeyFactory.getPKClause() + " AND height ="
                     + " (SELECT MAX(height) FROM " + table + dbKeyFactory.getPKClause() + ")")) {
            pstmtSelectToDelete.setInt(1, height);
            List<H2Key> h2Keys = new ArrayList<>();
            try (ResultSet rs = pstmtSelectToDelete.executeQuery()) {
                while (rs.next()) {
                    h2Keys.add(dbKeyFactory.newKey(rs));
                }
            }
            /*
            if (h2Keys.size() > 0 && Logger.isDebugEnabled()) {
                LoggerUtil.logDebug(String.format("rollback table %s found %d records to update to latest", table, h2Keys.size()));
            }
            */
            pstmtDelete.setInt(1, height);
            int deletedRecordsCount = pstmtDelete.executeUpdate();
            /*
            if (deletedRecordsCount > 0 && Logger.isDebugEnabled()) {
                LoggerUtil.logDebug(String.format("rollback table %s deleting %d records", table, deletedRecordsCount));
            }
            */
            for (H2Key h2Key : h2Keys) {
                int i = 1;
                i = h2Key.setH2KeyPK(pstmtSetLatest, i);
                i = h2Key.setH2KeyPK(pstmtSetLatest, i);
                pstmtSetLatest.executeUpdate();
                //Db.getCache(table).remove(h2Key);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void trim(final TransactionalH2 db, final String table, final int height, final H2KeyFactory dbKeyFactory) {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = db.getConnection();
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT " + dbKeyFactory.getPKColumns() + ", MAX(height) AS max_height"
                     + " FROM " + table + " WHERE height < ? GROUP BY " + dbKeyFactory.getPKColumns() + " HAVING COUNT(DISTINCT height) > 1");
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table + dbKeyFactory.getPKClause()
                     + " AND height < ? AND height >= 0");
             PreparedStatement pstmtDeleteDeleted = con.prepareStatement("DELETE FROM " + table + " WHERE height < ? AND height >= 0 AND latest = FALSE "
                     + " AND (" + dbKeyFactory.getPKColumns() + ") NOT IN (SELECT (" + dbKeyFactory.getPKColumns() + ") FROM "
                     + table + " WHERE height >= ?)")) {
            pstmtSelect.setInt(1, height);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                while (rs.next()) {
                    H2Key h2Key = dbKeyFactory.newKey(rs);
                    int maxHeight = rs.getInt("max_height");
                    int i = 1;
                    i = h2Key.setH2KeyPK(pstmtDelete, i);
                    pstmtDelete.setInt(i, maxHeight);
                    pstmtDelete.executeUpdate();
                }
                pstmtDeleteDeleted.setInt(1, height);
                pstmtDeleteDeleted.setInt(2, height);
                pstmtDeleteDeleted.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final boolean delete(T t) {
        return delete(t, false);
    }

    public final boolean delete(T t, boolean keepInCache) {
        if (t == null) {
            return false;
        }
        if (!h2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        H2Key h2Key = dbKeyFactory.newKey(t);
        try (Connection con = h2.getConnection();
             PreparedStatement pstmtCount = con.prepareStatement("SELECT 1 FROM " + table
                     + dbKeyFactory.getPKClause() + " AND height < ? LIMIT 1")) {
            int i = h2Key.setH2KeyPK(pstmtCount);
            pstmtCount.setInt(i, EcBlockchainImpl.getInstance().getHeight());
            try (ResultSet rs = pstmtCount.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + table
                            + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1")) {
                        h2Key.setH2KeyPK(pstmt);
                        pstmt.executeUpdate();
                        save(con, t);
                        pstmt.executeUpdate(); // delete after the save
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
            if (!keepInCache) {
                h2.getCache(table).remove(h2Key);
            }
        }
    }

}
