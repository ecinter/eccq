package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcException;

public class BlockNotAcceptedException extends EcException {

    private final EcBlockImpl block;

    BlockNotAcceptedException(String message, EcBlockImpl block) {
        super(message);
        this.block = block;
    }

    BlockNotAcceptedException(Throwable cause, EcBlockImpl block) {
        super(cause);
        this.block = block;
    }

    @Override
    public String getMessage() {
        return block == null ? super.getMessage() : super.getMessage() + ", block " + block.getStringECId() + " " + block.getJSONObject().toJSONString();
    }

}
