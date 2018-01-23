package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2KeyLongKey implements H2Key {

    private final long id;

    H2KeyLongKey(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    @Override
    public int setH2KeyPK(PreparedStatement pstmt) throws SQLException {
        return setH2KeyPK(pstmt, 1);
    }

    @Override
    public int setH2KeyPK(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setLong(index, id);
        return index + 1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof H2KeyLongKey && ((H2KeyLongKey) o).id == id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

}
