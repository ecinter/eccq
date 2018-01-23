package com.inesv.ecchain.kernel.core;

public class TransactionNotAcceptedException extends BlockNotAcceptedException {

    private final TransactionImpl transactionImpl;

    TransactionNotAcceptedException(String message, TransactionImpl transactionImpl) {
        super(message, transactionImpl.getBlock());
        this.transactionImpl = transactionImpl;
    }

    TransactionNotAcceptedException(Throwable cause, TransactionImpl transactionImpl) {
        super(cause, transactionImpl.getBlock());
        this.transactionImpl = transactionImpl;
    }

    public TransactionImpl getTransactionImpl() {
        return transactionImpl;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ", transactionImpl " + transactionImpl.getStringId() + " " + transactionImpl.getJSONObject().toJSONString();
    }
}
