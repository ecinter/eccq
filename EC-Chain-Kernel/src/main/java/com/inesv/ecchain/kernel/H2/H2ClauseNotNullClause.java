package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2ClauseNotNullClause extends H2Clause {

    public H2ClauseNotNullClause(String columnName) {
        super(" " + columnName + " IS NOT NULL ");
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        return index;
    }

}
