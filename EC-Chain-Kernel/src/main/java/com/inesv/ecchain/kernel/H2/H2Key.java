package com.inesv.ecchain.kernel.H2;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface H2Key {

    int setH2KeyPK(PreparedStatement pstmt) throws SQLException;

    int setH2KeyPK(PreparedStatement pstmt, int index) throws SQLException;

}
