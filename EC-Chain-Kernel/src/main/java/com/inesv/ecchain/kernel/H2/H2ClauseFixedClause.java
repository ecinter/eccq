package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class H2ClauseFixedClause extends H2Clause {

    public H2ClauseFixedClause(String clause) {
        super(clause);
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        return index;
    }

}
