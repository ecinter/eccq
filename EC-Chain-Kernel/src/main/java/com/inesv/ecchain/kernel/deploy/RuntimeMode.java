package com.inesv.ecchain.kernel.deploy;

public interface RuntimeMode {

    void init();

    void launchDesktopApplication();

    void shutdown();

    void alert(String message);
}
