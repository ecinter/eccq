package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.kernel.H2.BasicH2;
import com.inesv.ecchain.kernel.H2.TransactionalH2;


public final class H2 {

    public static final TransactionalH2 H2 = new TransactionalH2(new BasicH2.h2Properties()
            .maxCacheSize(PropertiesUtil.getKeyForInt("ec.dbCacheKB", 0))
            .dbUrl(PropertiesUtil.getKeyForString("ec.dbUrl", null))
            .dbType(PropertiesUtil.getKeyForString("ec.dbType", null))
            .dbDir(PropertiesUtil.getKeyForString("ec.dbDir", null))
            .dbParams(PropertiesUtil.getKeyForString("ec.dbParams", null))
            .dbUsername(PropertiesUtil.getKeyForString("ec.dbUsername", null))
            .dbPassword(PropertiesUtil.getKeyForString("ec.dbPassword", null))
            .maxConnections(PropertiesUtil.getKeyForInt("ec.maxDbConnections", 0))
            .loginTimeout(PropertiesUtil.getKeyForInt("ec.dbLoginTimeout", 0))
            .defaultLockTimeout(PropertiesUtil.getKeyForInt("ec.dbDefaultLockTimeout", 0) * 1000)
            .maxMemoryRows(PropertiesUtil.getKeyForInt("ec.dbMaxMemoryRows", 0))
    );

    private H2() {
    } // never

    public static void start() {
        H2.init(new EcH2Version());
    }

    public static void shutdown() {
        H2.shutdown();
    }

}
