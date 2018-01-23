package com.inesv.ecchain.kernel.deploy;

import java.nio.file.Paths;

public class UnixUserDirProviderEc extends EcDesktopUserDirProvider {

    private static final String EC_USER_HOME = Paths.get(System.getProperty("user.home"), ".ec").toString();

    @Override
    public String getEcUserHomeDir() {
        return EC_USER_HOME;
    }
}
