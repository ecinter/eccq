

package com.inesv.ecchain.common.util;




import com.inesv.ecchain.common.core.EcIOException;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;


public class CalculatingInputReader extends FilterReader {

    private long count = 0;

    private final long limit;

    public CalculatingInputReader(Reader reader, long limit) {
        super(reader);
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c != -1)
            incCount(1);
        return c;
    }

    @Override
    public int read(char [] cbuf) throws IOException {
        int c = super.read(cbuf);
        if (c != -1)
            incCount(c);
        return c;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int c = super.read(cbuf, off, len);
        if (c != -1)
            incCount(c);
        return c;
    }

    @Override
    public long skip(long n) throws IOException {
        long c = super.skip(n);
        if (c != -1)
            incCount(c);
        return c;
    }

    public long getCount() {
        return count;
    }

    private void incCount(long c) throws EcIOException {
        count += c;
        if (count > limit)
            throw new EcIOException("Maximum size exceeded: " + count);
    }
}
