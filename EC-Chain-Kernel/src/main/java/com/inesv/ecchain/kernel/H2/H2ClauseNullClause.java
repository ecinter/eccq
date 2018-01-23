package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2ClauseNullClause extends H2Clause {

    public H2ClauseNullClause(String columnName) {
        super(" " + columnName + " IS NULL ");
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        return index;
    }

}
