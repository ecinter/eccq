package com.inesv.ecchain.kernel.H2;

import java.sql.ResultSet;
import java.sql.SQLException;


public abstract class H2KeyLinkKeyFactory<T> extends H2KeyFactory<T> {

    private final String idColumnA;
    private final String idColumnB;

    public H2KeyLinkKeyFactory(String idColumnA, String idColumnB) {
        super(" WHERE " + idColumnA + " = ? AND " + idColumnB + " = ? ",
                idColumnA + ", " + idColumnB,
                " a." + idColumnA + " = b." + idColumnA + " AND a." + idColumnB + " = b." + idColumnB + " ");
        this.idColumnA = idColumnA;
        this.idColumnB = idColumnB;
    }

    @Override
    public H2Key newKey(ResultSet rs) throws SQLException {
        return new H2KeyLinkKey(rs.getLong(idColumnA), rs.getLong(idColumnB));
    }

    public H2Key newKey(long idA, long idB) {
        return new H2KeyLinkKey(idA, idB);
    }

}
