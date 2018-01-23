package com.inesv.ecchain.kernel.core;

class DoubleSpendingException extends RuntimeException {

    DoubleSpendingException(String message, long accountId, long confirmed, long unconfirmed) {
        super(message + " account: " + Long.toUnsignedString(accountId) + " confirmed: " + confirmed + " unconfirmed: " + unconfirmed);
    }

}
