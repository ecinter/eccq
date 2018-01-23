package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.common.util.Filter;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class FilteringIterator<T> implements Iterator<T>, Iterable<T>, AutoCloseable {

    private final H2Iterator<T> h2Iterator;
    private final Filter<T> filter;
    private final int from;
    private final int to;
    private T next;
    private boolean hasNext;
    private boolean iterated;
    private int count;

    public FilteringIterator(H2Iterator<T> h2Iterator, Filter<T> filter) {

        this(h2Iterator, filter, 0, Integer.MAX_VALUE);
    }

    public FilteringIterator(H2Iterator<T> h2Iterator, int from, int to) {
        this(h2Iterator, t -> true, from, to);
    }

    public FilteringIterator(H2Iterator<T> h2Iterator, Filter<T> filter, int from, int to) {
        this.h2Iterator = h2Iterator;
        this.filter = filter;
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean hasNext() {
        if (hasNext) {
            return true;
        }
        while (h2Iterator.hasNext() && count <= to) {
            next = h2Iterator.next();
            if (filter.ok(next)) {
                if (count >= from) {
                    count += 1;
                    hasNext = true;
                    return true;
                }
                count += 1;
            }
        }
        hasNext = false;
        return false;
    }

    @Override
    public T next() {
        if (hasNext) {
            hasNext = false;
            return next;
        }
        while (h2Iterator.hasNext() && count <= to) {
            next = h2Iterator.next();
            if (filter.ok(next)) {
                if (count >= from) {
                    count += 1;
                    hasNext = false;
                    return next;
                }
                count += 1;
            }
        }
        throw new NoSuchElementException();
    }

    @Override
    public void close() {
        h2Iterator.close();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        if (iterated) {
            throw new IllegalStateException("Already iterated");
        }
        iterated = true;
        return this;
    }

}
