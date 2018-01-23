package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2ClauseIntClause extends H2Clause {

    private final int value;

    public H2ClauseIntClause(String columnName, int value) {
        super(" " + columnName + " = ? ");
        this.value = value;
    }

    public H2ClauseIntClause(String columnName, H2ClauseOp operator, int value) {
        super(" " + columnName + operator.operator() + "? ");
        this.value = value;
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setInt(index, value);
        return index + 1;
    }

}
