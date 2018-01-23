package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class H2Clause {

    public static final H2Clause EMPTY_CLAUSE = new H2ClauseFixedClause(" TRUE ");
    private final String clause;

    protected H2Clause(String clause) {
        this.clause = clause;
    }

    final String getClause() {
        return clause;
    }

    protected abstract int set(PreparedStatement pstmt, int index) throws SQLException;

    public H2Clause and(final H2Clause other) {
        return new H2Clause(this.clause + " AND " + other.clause) {
            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                index = H2Clause.this.set(pstmt, index);
                index = other.set(pstmt, index);
                return index;
            }
        };
    }

}
