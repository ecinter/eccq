package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2ClauseLongClause extends H2Clause {

    private final long value;

    public H2ClauseLongClause(String columnName, long value) {
        super(" " + columnName + " = ? ");
        this.value = value;
    }

    public H2ClauseLongClause(String columnName, H2ClauseOp operator, long value) {
        super(" " + columnName + operator.operator() + "? ");
        this.value = value;
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setLong(index, value);
        return index + 1;
    }
}
