package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class H2ClauseLikeClause extends H2Clause {

    private final String prefix;

    public H2ClauseLikeClause(String columnName, String prefix) {
        super(" " + columnName + " LIKE ? ");
        this.prefix = prefix.replace("%", "\\%").replace("_", "\\_") + '%';
    }

    @Override
    protected int set(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setString(index, prefix);
        return index + 1;
    }
}
