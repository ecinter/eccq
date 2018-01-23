package com.inesv.ecchain.kernel.H2;

public abstract class PersistentH2Table<T> extends EntityH2Table<T> {

    protected PersistentH2Table(String table, H2KeyFactory<T> dbKeyFactory) {
        super(table, dbKeyFactory, false, null);
    }

    protected PersistentH2Table(String table, H2KeyFactory<T> dbKeyFactory, String fullTextSearchColumns) {
        super(table, dbKeyFactory, false, fullTextSearchColumns);
    }

    PersistentH2Table(String table, H2KeyFactory<T> dbKeyFactory, boolean multiversion, String fullTextSearchColumns) {
        super(table, dbKeyFactory, multiversion, fullTextSearchColumns);
    }

    @Override
    public void rollback(int height) {
    }

    @Override
    public final void truncate() {
    }

    @Override
    public final boolean isLasting() {
        return true;
    }

}
