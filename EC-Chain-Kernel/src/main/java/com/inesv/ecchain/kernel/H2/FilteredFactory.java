package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.Statement;


public interface FilteredFactory {


    Statement establishStatement(Statement stmt);

    PreparedStatement establishPreparedStatement(PreparedStatement stmt, String sql);
}
