package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2ClauseByteClause extends H2Clause {

    private final byte value;

    public H2ClauseByteClause(String columnName, byte value) {
        super(" " + columnName + " = ? ");
        this.value = value;
    }

    public H2ClauseByteClause(String columnName, H2ClauseOp operator, byte value) {
        super(" " + columnName + operator.operator() + "? ");
        this.value = value;
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setByte(index, value);
        return index + 1;
    }

}
