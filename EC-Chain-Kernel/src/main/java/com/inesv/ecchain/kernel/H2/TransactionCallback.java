package com.inesv.ecchain.kernel.H2;


public interface TransactionCallback {

    void commit();

    void rollback();
}
