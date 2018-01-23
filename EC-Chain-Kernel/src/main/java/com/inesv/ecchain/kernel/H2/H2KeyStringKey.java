package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class H2KeyStringKey implements H2Key {

    private final String id;

    H2KeyStringKey(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public int setH2KeyPK(PreparedStatement pstmt) throws SQLException {
        return setH2KeyPK(pstmt, 1);
    }

    @Override
    public int setH2KeyPK(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setString(index, id);
        return index + 1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof H2KeyStringKey && (id != null ? id.equals(((H2KeyStringKey) o).id) : ((H2KeyStringKey) o).id == null);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

}
