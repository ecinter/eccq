package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class EntityH2Table<T> extends DerivedH2Table {

    protected final H2KeyFactory<T> dbKeyFactory;
    private final boolean ecmultiversion;
    private final String defaultSort;
    private final String fullTextSearchColumns;

    protected EntityH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        this(table, dbKeyFactory, false, null);
    }

    protected EntityH2Table(String table, H2KeyFactory<T> dbKeyFactory, String fullTextSearchColumns) {
        this(table, dbKeyFactory, false, fullTextSearchColumns);
    }

    EntityH2Table(String table, H2KeyFactory<T> dbKeyFactory, boolean ecmultiversion, String fullTextSearchColumns) {
        super(table);
        this.dbKeyFactory = dbKeyFactory;
        this.ecmultiversion = ecmultiversion;
        this.defaultSort = " ORDER BY " + (ecmultiversion ? dbKeyFactory.getPKColumns() : " height DESC, db_id DESC ");
        this.fullTextSearchColumns = fullTextSearchColumns;
    }

    protected abstract T load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException;

    protected abstract void save(Connection con, T t) throws SQLException;

    protected String defaultSort() {
        return defaultSort;
    }

    protected void clearCache() {
        h2.clearCache(table);
    }

    public void checkAvailable(int height) {
        if (ecmultiversion) {
            int minRollBackHeight = isLasting() && EcBlockchainProcessorImpl.getInstance().isScanning() ?
                    Math.max(EcBlockchainProcessorImpl.getInstance().getInitialScanHeight() - Constants.MAX_ROLLBACK, 0)
                    : EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight();
            if (height < minRollBackHeight) {
                throw new IllegalArgumentException("Historical data as of height " + height + " not available.");
            }
        }
        if (height > EcBlockchainImpl.getInstance().getHeight()) {
            throw new IllegalArgumentException("Height " + height + " exceeds blockchain height " + EcBlockchainImpl.getInstance().getHeight());
        }
    }

    public final T newEntity(H2Key h2Key) {
        boolean cache = h2.isInTransaction();
        if (cache) {
            T t = (T) h2.getCache(table).get(h2Key);
            if (t != null) {
                return t;
            }
        }
        T t = dbKeyFactory.newEntity(h2Key);
        if (cache) {
            h2.getCache(table).put(h2Key, t);
        }
        return t;
    }

    public final T get(H2Key h2Key) {
        return get(h2Key, true);
    }

    public final T get(H2Key h2Key, boolean cache) {
        if (cache && h2.isInTransaction()) {
            T t = (T) h2.getCache(table).get(h2Key);
            if (t != null) {
                return t;
            }
        }
        try (Connection con = h2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table + dbKeyFactory.getPKClause()
                     + (ecmultiversion ? " AND latest = TRUE LIMIT 1" : ""))) {
            h2Key.setH2KeyPK(pstmt);
            return get(con, pstmt, cache);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T get(H2Key h2Key, int height) {
        if (height < 0 || doesNotExceed(height)) {
            return get(h2Key);
        }
        checkAvailable(height);
        try (Connection con = h2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table + dbKeyFactory.getPKClause()
                     + " AND height <= ?" + (ecmultiversion ? " AND (latest = TRUE OR EXISTS ("
                     + "SELECT 1 FROM " + table + dbKeyFactory.getPKClause() + " AND height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
            int i = h2Key.setH2KeyPK(pstmt);
            pstmt.setInt(i, height);
            if (ecmultiversion) {
                i = h2Key.setH2KeyPK(pstmt, ++i);
                pstmt.setInt(i, height);
            }
            return get(con, pstmt, false);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T getBy(H2Clause h2Clause) {
        try (Connection con = h2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table
                     + " WHERE " + h2Clause.getClause() + (ecmultiversion ? " AND latest = TRUE LIMIT 1" : ""))) {
            h2Clause.set(pstmt, 1);
            return get(con, pstmt, true);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T getBy(H2Clause h2Clause, int height) {
        if (height < 0 || doesNotExceed(height)) {
            return getBy(h2Clause);
        }
        checkAvailable(height);
        try (Connection con = h2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table + " AS a WHERE " + h2Clause.getClause()
                     + " AND height <= ?" + (ecmultiversion ? " AND (latest = TRUE OR EXISTS ("
                     + "SELECT 1 FROM " + table + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                     + " AND b.height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
            int i = 0;
            i = h2Clause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (ecmultiversion) {
                pstmt.setInt(++i, height);
            }
            return get(con, pstmt, false);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private T get(Connection con, PreparedStatement pstmt, boolean cache) throws SQLException {
        final boolean doCache = cache && h2.isInTransaction();
        try (ResultSet rs = pstmt.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            T t = null;
            H2Key h2Key = null;
            if (doCache) {
                h2Key = dbKeyFactory.newKey(rs);
                t = (T) h2.getCache(table).get(h2Key);
            }
            if (t == null) {
                t = load(con, rs, h2Key);
                if (doCache) {
                    h2.getCache(table).put(h2Key, t);
                }
            }
            if (rs.next()) {
                throw new RuntimeException("Multiple records found");
            }
            return t;
        }
    }

    public final H2Iterator<T> getManyBy(H2Clause h2Clause, int from, int to) {
        return getManyBy(h2Clause, from, to, defaultSort());
    }

    public final H2Iterator<T> getManyBy(H2Clause h2Clause, int from, int to, String sort) {
        Connection con = null;
        try {
            con = h2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table
                    + " WHERE " + h2Clause.getClause() + (ecmultiversion ? " AND latest = TRUE " : " ") + sort
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            i = h2Clause.set(pstmt, ++i);
            i = H2Utils.setLimits(i, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final H2Iterator<T> getManyBy(H2Clause h2Clause, int height, int from, int to) {
        return getManyBy(h2Clause, height, from, to, defaultSort());
    }

    public final H2Iterator<T> getManyBy(H2Clause h2Clause, int height, int from, int to, String sort) {
        if (height < 0 || doesNotExceed(height)) {
            return getManyBy(h2Clause, from, to, sort);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = h2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table + " AS a WHERE " + h2Clause.getClause()
                    + "AND a.height <= ?" + (ecmultiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + table + " AS b WHERE " + dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
                    + "AND NOT EXISTS (SELECT 1 FROM " + table + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height <= ? AND b.height > a.height))) "
                    : " ") + sort
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            i = h2Clause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (ecmultiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            i = H2Utils.setLimits(++i, pstmt, from, to);
            return getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final H2Iterator<T> getManyBy(Connection con, PreparedStatement pstmt, boolean cache) {
        final boolean doCache = cache && h2.isInTransaction();
        return new H2Iterator<>(con, pstmt, (connection, rs) -> {
            T t = null;
            H2Key h2Key = null;
            if (doCache) {
                h2Key = dbKeyFactory.newKey(rs);
                t = (T) h2.getCache(table).get(h2Key);
            }
            if (t == null) {
                t = load(connection, rs, h2Key);
                if (doCache) {
                    h2.getCache(table).put(h2Key, t);
                }
            }
            return t;
        });
    }

    public final H2Iterator<T> search(String query, H2Clause h2Clause, int from, int to) {
        return search(query, h2Clause, from, to, " ORDER BY ft.score DESC ");
    }

    public final H2Iterator<T> search(String query, H2Clause h2Clause, int from, int to, String sort) {
        Connection con = null;
        try {
            con = h2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT " + table + ".*, ft.score FROM " + table +
                    ", ftl_search('PUBLIC', '" + table + "', ?, 2147483647, 0) ft "
                    + " WHERE " + table + ".db_id = ft.keys[0] "
                    + (ecmultiversion ? " AND " + table + ".latest = TRUE " : " ")
                    + " AND " + h2Clause.getClause() + sort
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setString(++i, query);
            i = h2Clause.set(pstmt, ++i);
            i = H2Utils.setLimits(i, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final H2Iterator<T> searchByGoodsName(String query, H2Clause h2Clause, int from, int to, String sort){
        Connection con = null;
        try {
            con = h2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table
                    + " WHERE "+table+".name like '%"+query +"%' "+ (ecmultiversion ? " AND latest = TRUE " : " ") + sort
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            i = H2Utils.setLimits(i, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final H2Iterator<T> getAll(int from, int to) {
        return getAll(from, to, defaultSort());
    }

    public final H2Iterator<T> getAll(int from, int to, String sort) {
        Connection con = null;
        try {
            con = h2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table
                    + (ecmultiversion ? " WHERE latest = TRUE " : " ") + sort
                    + H2Utils.limitsClause(from, to));
            H2Utils.setLimits(1, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final H2Iterator<T> getAll(int height, int from, int to) {
        return getAll(height, from, to, defaultSort());
    }

    public final H2Iterator<T> getAll(int height, int from, int to, String sort) {
        if (height < 0 || doesNotExceed(height)) {
            return getAll(from, to, sort);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = h2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table + " AS a WHERE height <= ?"
                    + (ecmultiversion ? " AND (latest = TRUE OR (latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + table + " AS b WHERE b.height > ? AND " + dbKeyFactory.getSelfJoinClause()
                    + ") AND NOT EXISTS (SELECT 1 FROM " + table + " AS b WHERE b.height <= ? AND " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height > a.height))) " : " ") + sort
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setInt(++i, height);
            if (ecmultiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            i = H2Utils.setLimits(++i, pstmt, from, to);
            return getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getCount() {
        try (Connection con = h2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + table
                     + (ecmultiversion ? " WHERE latest = TRUE" : ""))) {
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getCount(H2Clause h2Clause) {
        try (Connection con = h2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + table
                     + " WHERE " + h2Clause.getClause() + (ecmultiversion ? " AND latest = TRUE" : ""))) {
            h2Clause.set(pstmt, 1);
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getCount(H2Clause h2Clause, int height) {
        if (height < 0 || doesNotExceed(height)) {
            return getCount(h2Clause);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = h2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + table + " AS a WHERE " + h2Clause.getClause()
                    + "AND a.height <= ?" + (ecmultiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + table + " AS b WHERE " + dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
                    + "AND NOT EXISTS (SELECT 1 FROM " + table + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height <= ? AND b.height > a.height))) "
                    : " "));
            int i = 0;
            i = h2Clause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (ecmultiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            return getCount(pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    private int getCount(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public final void insert(T t) {
        if (!h2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        H2Key h2Key = dbKeyFactory.newKey(t);
        if (h2Key == null) {
            throw new RuntimeException("H2Key not set");
        }
        T cachedT = (T) h2.getCache(table).get(h2Key);
        if (cachedT == null) {
            h2.getCache(table).put(h2Key, t);
        } else if (t != cachedT) { // not a bug
            LoggerUtil.logDebug("In cache : " + cachedT.toString() + ", inserting " + t.toString());
            throw new IllegalStateException("Different instance found in Db cache, perhaps trying to save an object "
                    + "that was read outside the current transaction");
        }
        try (Connection con = h2.getConnection()) {
            if (ecmultiversion) {
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + table
                        + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1")) {
                    h2Key.setH2KeyPK(pstmt);
                    pstmt.executeUpdate();
                }
            }
            save(con, t);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void rollback(int height) {
        if (ecmultiversion) {
            VersionedEntityH2Table.rollback(h2, table, height, dbKeyFactory);
        } else {
            super.rollback(height);
        }
    }

    @Override
    public void trim(int height) {
        if (ecmultiversion) {
            VersionedEntityH2Table.trim(h2, table, height, dbKeyFactory);
        } else {
            super.trim(height);
        }
    }

    @Override
    public final void establishSearchIndex(Connection con) throws SQLException {
        if (fullTextSearchColumns != null) {
            LoggerUtil.logDebug("Creating search index on " + table + " (" + fullTextSearchColumns + ")");
            FullTextTrigger.createIndex(con, "PUBLIC", table.toUpperCase(), fullTextSearchColumns.toUpperCase());
        }
    }

    private boolean doesNotExceed(int height) {
        return EcBlockchainImpl.getInstance().getHeight() <= height && !(isLasting() && EcBlockchainProcessorImpl.getInstance().isScanning());
    }

}
