package com.inesv.ecchain.kernel.H2;

public abstract class VersionedPersistentH2Table<T> extends VersionedPrunableH2Table<T> {

    protected VersionedPersistentH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        super(table, dbKeyFactory);
    }

    @Override
    protected final void prune() {
    }

}
