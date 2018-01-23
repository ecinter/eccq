package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.kernel.peer.Peer;

class PeerBlock {

    private final Peer peer;

    private final EcBlockImpl block;

    public PeerBlock(Peer peer, EcBlockImpl block) {
        this.peer = peer;
        this.block = block;
    }

    public Peer getPeer() {
        return peer;
    }

    public EcBlockImpl getBlock() {
        return block;
    }
}
