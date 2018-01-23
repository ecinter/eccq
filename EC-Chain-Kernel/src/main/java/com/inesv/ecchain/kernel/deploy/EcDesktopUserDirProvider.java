package com.inesv.ecchain.kernel.deploy;

import java.nio.file.Paths;

abstract class EcDesktopUserDirProvider implements DirProvider {

    @Override
    public String getH2Dir(String dbDir) {
        return Paths.get(getEcUserHomeDir()).resolve(Paths.get(dbDir)).toString();
    }

    @Override
    public abstract String getEcUserHomeDir();

}
