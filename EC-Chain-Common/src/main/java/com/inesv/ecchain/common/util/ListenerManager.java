

package com.inesv.ecchain.common.util;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ListenerManager<T,E extends Enum<E>> {

    private final ConcurrentHashMap<Enum<E>, List<Listener<T>>> listeners_Map = new ConcurrentHashMap<>();

    public boolean addECListener(Listener<T> listener, Enum<E> eventType) {
        synchronized (eventType) {
            List<Listener<T>> listeners = listeners_Map.get(eventType);
            if (listeners == null) {
                listeners = new CopyOnWriteArrayList<>();
                listeners_Map.put(eventType, listeners);
            }
            return listeners.add(listener);
        }
    }

    public boolean removeECListener(Listener<T> listener, Enum<E> eventType) {
        synchronized (eventType) {
            List<Listener<T>> listeners = listeners_Map.get(eventType);
            if (listeners != null) {
                return listeners.remove(listener);
            }
        }
        return false;
    }

    public void notify(T t, Enum<E> eventType) {
        List<Listener<T>> listeners = listeners_Map.get(eventType);
        if (listeners != null) {
            for (Listener<T> listener : listeners) {
                listener.notify(t);
            }
        }
    }

}
