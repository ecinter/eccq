package com.inesv.ecchain.kernel.deploy;

public interface DirProvider {

    String getH2Dir(String dbDir);

    String getEcUserHomeDir();
}
