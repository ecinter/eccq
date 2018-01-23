package com.inesv.ecchain.kernel.deploy;

import org.springframework.stereotype.Component;

@Component
public class EcCommandLineMode implements RuntimeMode {

    @Override
    public void init() {
    }

    @Override
    public void launchDesktopApplication() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void alert(String message) {
    }
}
