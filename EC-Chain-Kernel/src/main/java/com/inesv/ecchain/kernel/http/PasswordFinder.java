package com.inesv.ecchain.kernel.http;

import java.nio.ByteBuffer;


class PasswordFinder {

    static int process(ByteBuffer buffer, String[] secrets) {
        try {
            int[] pos = new int[secrets.length];
            byte[][] tokens = new byte[secrets.length][];
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = secrets[i].getBytes();
            }
            while (buffer.hasRemaining()) {
                byte current = buffer.get();
                for (int i = 0; i < tokens.length; i++) {
                    if (current != tokens[i][pos[i]]) {
                        pos[i] = 0;
                        continue;
                    }
                    pos[i]++;
                    if (pos[i] == tokens[i].length) {
                        return buffer.position() - tokens[i].length;
                    }
                }
            }
            return -1;
        } finally {
            buffer.rewind();
        }
    }
}
