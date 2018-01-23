

package com.inesv.ecchain.common.util;

public interface Observable<T,E extends Enum<E>> {

    boolean addECListener(Listener<T> listener, E eventType);

    boolean removeECListener(Listener<T> listener, E eventType);

}
