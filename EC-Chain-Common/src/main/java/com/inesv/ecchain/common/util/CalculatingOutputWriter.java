

package com.inesv.ecchain.common.util;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

public class CalculatingOutputWriter extends FilterWriter {

    private long count = 0;

    public CalculatingOutputWriter(Writer writer) {
        super(writer);
    }

    @Override
    public void write(int c) throws IOException {
        super.write(c);
        count++;
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        super.write(cbuf);
        count += cbuf.length;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        super.write(cbuf, off, len);
        count += len;
    }

    @Override
    public void write(String s) throws IOException {
        super.write(s);
        count += s.length();
    }

    @Override
    public void write(String s, int off, int len) throws IOException {
        super.write(s, off, len);
        count += len;
    }

    public long getCount() {
        return count;
    }
}
