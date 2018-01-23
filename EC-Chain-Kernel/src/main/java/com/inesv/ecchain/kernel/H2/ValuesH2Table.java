package com.inesv.ecchain.kernel.H2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class ValuesH2Table<T, V> extends DerivedH2Table {

    protected final H2KeyFactory<T> dbKeyFactory;

    private final boolean multiversion;

    protected ValuesH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        this(table, dbKeyFactory, false);
    }

    ValuesH2Table(String table, H2KeyFactory<T> dbKeyFactory, boolean multiversion) {
        super(table);
        this.dbKeyFactory = dbKeyFactory;
        this.multiversion = multiversion;
    }

    protected abstract V load(Connection con, ResultSet rs) throws SQLException;

    protected abstract void save(Connection con, T t, V v) throws SQLException;

    public final List<V> get(H2Key h2Key) {
        List<V> values;
        if (h2.isInTransaction()) {
            values = (List<V>) h2.getCache(table).get(h2Key);
            if (values != null) {
                return values;
            }
        }
        try (Connection con = h2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table + dbKeyFactory.getPKClause()
                     + (multiversion ? " AND latest = TRUE" : "") + " ORDER BY db_id")) {
            h2Key.setH2KeyPK(pstmt);
            values = get(con, pstmt);
            if (h2.isInTransaction()) {
                h2.getCache(table).put(h2Key, values);
            }
            return values;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private List<V> get(Connection con, PreparedStatement pstmt) {
        try {
            List<V> result = new ArrayList<>();
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(load(con, rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final void insert(T t, List<V> values) {
        if (!h2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        H2Key h2Key = dbKeyFactory.newKey(t);
        if (h2Key == null) {
            throw new RuntimeException("H2Key not set");
        }
        h2.getCache(table).put(h2Key, values);
        try (Connection con = h2.getConnection()) {
            if (multiversion) {
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + table
                        + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE")) {
                    h2Key.setH2KeyPK(pstmt);
                    pstmt.executeUpdate();
                }
            }
            for (V v : values) {
                save(con, t, v);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public final void rollback(int height) {
        if (multiversion) {
            VersionedEntityH2Table.rollback(h2, table, height, dbKeyFactory);
        } else {
            super.rollback(height);
        }
    }

    @Override
    public final void trim(int height) {
        if (multiversion) {
            VersionedEntityH2Table.trim(h2, table, height, dbKeyFactory);
        } else {
            super.trim(height);
        }
    }

}
