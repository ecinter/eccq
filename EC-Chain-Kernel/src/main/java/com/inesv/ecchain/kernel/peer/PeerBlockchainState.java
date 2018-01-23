package com.inesv.ecchain.kernel.peer;

public enum PeerBlockchainState {
    UP_TO_DATE,
    DOWNLOADING,
    LIGHT_CLIENT,
    FORK
}
