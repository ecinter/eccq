package com.inesv.ecchain.kernel.http;


class EventRegistration {


    private final Enum<? extends Enum> event;


    private final long accountId;


    EventRegistration(Enum<? extends Enum> event, long accountId) {
        this.event = event;
        this.accountId = accountId;
    }


    public Enum<? extends Enum> getEvent() {
        return event;
    }


    public long getAccountId() {
        return accountId;
    }
}
