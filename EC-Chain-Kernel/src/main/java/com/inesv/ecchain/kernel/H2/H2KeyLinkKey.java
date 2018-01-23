package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2KeyLinkKey implements H2Key {

    private final long idA;
    private final long idB;

    H2KeyLinkKey(long idA, long idB) {
        this.idA = idA;
        this.idB = idB;
    }

    public long[] getId() {
        return new long[]{idA, idB};
    }

    @Override
    public int setH2KeyPK(PreparedStatement pstmt) throws SQLException {
        return setH2KeyPK(pstmt, 1);
    }

    @Override
    public int setH2KeyPK(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setLong(index, idA);
        pstmt.setLong(index + 1, idB);
        return index + 2;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof H2KeyLinkKey && ((H2KeyLinkKey) o).idA == idA && ((H2KeyLinkKey) o).idB == idB;
    }

    @Override
    public int hashCode() {
        return (int) (idA ^ (idA >>> 32)) ^ (int) (idB ^ (idB >>> 32));
    }

}
