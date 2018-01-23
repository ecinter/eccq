package com.inesv.ecchain.kernel.http;

import java.util.List;


class PendingEvent {


    private final String name;


    private final String id;


    private final List<String> idList;


    private Thread thread;


    public PendingEvent(String name, String id) {
        this.name = name;
        this.id = id;
        this.idList = null;
    }


    public PendingEvent(String name, List<String> idList) {
        this.name = name;
        this.idList = idList;
        this.id = null;
    }


    public String getName() {
        return name;
    }

    public boolean isList() {
        return (idList != null);
    }


    public String getId() {
        return id;
    }


    public List<String> getIdList() {
        return idList;
    }


    public Thread getThread() {
        return thread;
    }


    public void setThread(Thread thread) {
        this.thread = thread;
    }
}
