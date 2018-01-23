package com.inesv.ecchain.kernel.H2;

import java.sql.ResultSet;
import java.sql.SQLException;


public abstract class H2KeyStringKeyFactory<T> extends H2KeyFactory<T> {

    private final String idColumn;

    public H2KeyStringKeyFactory(String idColumn) {
        super(" WHERE " + idColumn + " = ? ",
                idColumn,
                " a." + idColumn + " = b." + idColumn + " ");
        this.idColumn = idColumn;
    }

    @Override
    public H2Key newKey(ResultSet rs) throws SQLException {
        return new H2KeyStringKey(rs.getString(idColumn));
    }

    public H2Key newKey(String id) {
        return new H2KeyStringKey(id);
    }

}
