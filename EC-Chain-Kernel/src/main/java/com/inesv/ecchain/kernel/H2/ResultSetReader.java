package com.inesv.ecchain.kernel.H2;

import java.sql.Connection;
import java.sql.ResultSet;

public interface ResultSetReader<T> {
    T get(Connection con, ResultSet rs) throws Exception;
}
