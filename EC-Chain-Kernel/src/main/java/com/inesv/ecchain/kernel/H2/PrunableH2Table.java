package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.common.util.LoggerUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class PrunableH2Table<T> extends PersistentH2Table<T> {

    protected PrunableH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        super(table, dbKeyFactory);
    }

    PrunableH2Table(String table, H2KeyFactory<T> dbKeyFactory, boolean multiversion, String fullTextSearchColumns) {
        super(table, dbKeyFactory, multiversion, fullTextSearchColumns);
    }

    @Override
    public final void trim(int height) {
        prune();
        super.trim(height);
    }

    protected void prune() {
        if (Constants.EC_ENABLE_PRUNING) {
            try (Connection con = h2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("DELETE FROM " + table + " WHERE transaction_timestamp < ?")) {
                pstmt.setInt(1, new EcTime.EpochEcTime().getTime() - Constants.EC_MAX_PRUNABLE_LIFETIME);
                int deleted = pstmt.executeUpdate();
                if (deleted > 0) {
                    LoggerUtil.logDebug("Deleted " + deleted + " expired prunable data from " + table);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    }

}
