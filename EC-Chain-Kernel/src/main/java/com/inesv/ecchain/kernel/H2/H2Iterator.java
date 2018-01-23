package com.inesv.ecchain.kernel.H2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class H2Iterator<T> implements Iterator<T>, Iterable<T>, AutoCloseable {

    private final Connection con;
    private final PreparedStatement pstmt;
    private final ResultSetReader<T> rsReader;
    private final ResultSet rs;
    private boolean hasNext;
    private boolean iterated;
    public H2Iterator(Connection con, PreparedStatement pstmt, ResultSetReader<T> rsReader) {
        this.con = con;
        this.pstmt = pstmt;
        this.rsReader = rsReader;
        try {
            this.rs = pstmt.executeQuery();
            this.hasNext = rs.next();
        } catch (SQLException e) {
            H2Utils.h2close(pstmt, con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public boolean hasNext() {
        if (!hasNext) {
            H2Utils.h2close(rs, pstmt, con);
        }
        return hasNext;
    }

    @Override
    public T next() {
        if (!hasNext) {
            H2Utils.h2close(rs, pstmt, con);
            throw new NoSuchElementException();
        }
        try {
            T result = rsReader.get(con, rs);
            hasNext = rs.next();
            return result;
        } catch (Exception e) {
            H2Utils.h2close(rs, pstmt, con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Removal not supported");
    }

    @Override
    public void close() {
        H2Utils.h2close(rs, pstmt, con);
    }

    @Override
    public Iterator<T> iterator() {
        if (iterated) {
            throw new IllegalStateException("Already iterated");
        }
        iterated = true;
        return this;
    }

}
