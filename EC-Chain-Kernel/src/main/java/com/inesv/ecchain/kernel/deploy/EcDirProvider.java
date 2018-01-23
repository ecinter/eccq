package com.inesv.ecchain.kernel.deploy;

import java.nio.file.Paths;

public class EcDirProvider implements DirProvider {

    @Override
    public String getH2Dir(String dbDir) {
        return dbDir;
    }

    @Override
    public String getEcUserHomeDir() {
        return Paths.get(".").toAbsolutePath().getParent().toString();
    }

}
