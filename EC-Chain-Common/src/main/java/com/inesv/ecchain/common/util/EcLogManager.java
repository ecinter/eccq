

package com.inesv.ecchain.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class EcLogManager extends LogManager {

    private volatile boolean loggingreconfiguration = false;

    public EcLogManager() {
        super();
    }

    @Override
    public void readConfiguration(InputStream inStream) throws IOException, SecurityException {
        loggingreconfiguration = true;
        super.readConfiguration(inStream);
        loggingreconfiguration = false;
    }

    @Override
    public void reset() {
        if (loggingreconfiguration)
            super.reset();
    }

}
