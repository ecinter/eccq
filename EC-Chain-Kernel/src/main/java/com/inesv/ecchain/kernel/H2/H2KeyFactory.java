package com.inesv.ecchain.kernel.H2;

import java.sql.ResultSet;
import java.sql.SQLException;


public abstract class H2KeyFactory<T> {

    private final String pkClause;
    private final String pkColumns;
    private final String selfJoinClause;

    protected H2KeyFactory(String pkClause, String pkColumns, String selfJoinClause) {
        this.pkClause = pkClause;
        this.pkColumns = pkColumns;
        this.selfJoinClause = selfJoinClause;
    }

    public abstract H2Key newKey(T t);

    public abstract H2Key newKey(ResultSet rs) throws SQLException;

    public T newEntity(H2Key h2Key) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public final String getPKClause() {
        return pkClause;
    }

    public final String getPKColumns() {
        return pkColumns;
    }

    public final String getSelfJoinClause() {
        return selfJoinClause;
    }

}
