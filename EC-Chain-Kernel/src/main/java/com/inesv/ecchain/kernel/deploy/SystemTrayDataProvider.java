package com.inesv.ecchain.kernel.deploy;

import java.io.File;
import java.net.URI;

public class SystemTrayDataProvider {

    private final URI wallet;
    private final File logFile;

    public SystemTrayDataProvider( URI wallet, File logFile) {
        this.wallet = wallet;
        this.logFile = logFile;
    }

    public URI getEcWallet() {
        return wallet;
    }

    public File getEcLogFile() {
        return logFile;
    }
}
