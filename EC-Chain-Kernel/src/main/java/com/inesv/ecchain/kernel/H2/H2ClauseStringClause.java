package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2ClauseStringClause extends H2Clause {

    private final String value;

    public H2ClauseStringClause(String columnName, String value) {
        super(" " + columnName + " = ? ");
        this.value = value;
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setString(index, value);
        return index + 1;
    }

}
