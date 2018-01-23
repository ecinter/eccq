

package com.inesv.ecchain.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

public class MemoryProcessor extends Handler {

    private static final int EC_DEFAULT_SIZE = 100;

    private static final int EC_OFF_VALUE = Level.OFF.intValue();

    private final LogRecord[] buffer;

    private int start = 0;

    private int count = 0;

    private Level level;

    public MemoryProcessor() {
        LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();
        String value;
        //
        // Allocate the ring buffer
        //
        int bufferSize;
        try {
            value = manager.getProperty(cname+".size");
            if (value != null)
                bufferSize = Math.max(Integer.valueOf(value.trim()), 10);
            else
                bufferSize = EC_DEFAULT_SIZE;
        } catch (NumberFormatException exc) {
            bufferSize = EC_DEFAULT_SIZE;
        }
        buffer = new LogRecord[bufferSize];
        //
        // Get publish level
        //
        try {
            value = manager.getProperty(cname+".level");
            if (value != null) {
                level = Level.parse(value.trim());
            } else {
                level = Level.ALL;
            }
        } catch (IllegalArgumentException exc) {
            level = Level.ALL;
        }
    }

    public List<String> getMessages(int msgCount) {
        List<String> rtnList = new ArrayList<>(buffer.length);
        synchronized(buffer) {
            int rtnSize = Math.min(msgCount, count);
            int pos = (start + (count-rtnSize))%buffer.length;
            Formatter formatter = getFormatter();
            for (int i=0; i<rtnSize; i++) {
                rtnList.add(formatter.format(buffer[pos++]));
                if (pos == buffer.length)
                    pos = 0;
            }
        }
        return rtnList;
    }

    @Override
    public void publish(LogRecord record) {
        if (record != null && record.getLevel().intValue() >= level.intValue() && level.intValue() != EC_OFF_VALUE) {
            synchronized(buffer) {
                int ix = (start+count)%buffer.length;
                buffer[ix] = record;
                if (count < buffer.length) {
                    count++;
                } else {
                    start++;
                    start %= buffer.length;
                }
            }
        }
    }

    @Override
    public void flush() {
        synchronized(buffer) {
            start = 0;
            count = 0;
        }
    }

    @Override
    public void close() {
        level = Level.OFF;
    }
}
