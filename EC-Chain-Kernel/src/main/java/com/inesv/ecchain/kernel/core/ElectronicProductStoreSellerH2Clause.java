package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.kernel.H2.H2Clause;

import java.sql.PreparedStatement;
import java.sql.SQLException;


final class ElectronicProductStoreSellerH2Clause extends H2Clause {

    private final long sellerId;

    ElectronicProductStoreSellerH2Clause(long sellerId, boolean inStockOnly) {
        super(" seller_id = ? " + (inStockOnly ? "AND delisted = FALSE AND quantity > 0" : ""));
        this.sellerId = sellerId;
    }

    @Override
    public int set(PreparedStatement pstmt, int index) throws SQLException {
        pstmt.setLong(index++, sellerId);
        return index;
    }

}
