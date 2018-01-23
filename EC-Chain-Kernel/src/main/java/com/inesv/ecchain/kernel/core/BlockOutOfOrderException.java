package com.inesv.ecchain.kernel.core;

public class BlockOutOfOrderException extends BlockNotAcceptedException {

    BlockOutOfOrderException(String message, EcBlockImpl block) {
        super(message, block);
    }

}
