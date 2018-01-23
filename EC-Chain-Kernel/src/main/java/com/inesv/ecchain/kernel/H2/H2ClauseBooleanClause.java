package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class H2ClauseBooleanClause extends H2Clause {

    private final boolean value;

    public H2ClauseBooleanClause(String columnName, boolean value) {
        super(" " + columnName + " = ? ");
        this.value = value;
    }


    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setBoolean(index, value);
        return index + 1;
    }

}
