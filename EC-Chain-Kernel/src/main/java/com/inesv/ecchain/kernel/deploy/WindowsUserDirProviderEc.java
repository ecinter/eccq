package com.inesv.ecchain.kernel.deploy;

import java.nio.file.Paths;

public class WindowsUserDirProviderEc extends EcDesktopUserDirProvider {

    private static final String EC_USER_HOME = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "EC").toString();

    @Override
    public String getEcUserHomeDir() {
        return EC_USER_HOME;
    }
}
