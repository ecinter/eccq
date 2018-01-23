package com.inesv.ecchain.kernel.H2;

import java.sql.ResultSet;
import java.sql.SQLException;


public abstract class H2KeyLongKeyFactory<T> extends H2KeyFactory<T> {

    private final String idColumn;

    public H2KeyLongKeyFactory(String idColumn) {
        super(" WHERE " + idColumn + " = ? ",
                idColumn,
                " a." + idColumn + " = b." + idColumn + " ");
        this.idColumn = idColumn;
    }

    @Override
    public H2Key newKey(ResultSet rs) throws SQLException {
        return new H2KeyLongKey(rs.getLong(idColumn));
    }

    public H2Key newKey(long id) {
        return new H2KeyLongKey(id);
    }

}
