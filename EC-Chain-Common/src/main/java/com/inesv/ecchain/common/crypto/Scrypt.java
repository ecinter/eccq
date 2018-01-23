

package com.inesv.ecchain.common.crypto;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@SuppressWarnings({"PointlessBitwiseExpression", "PointlessArithmeticExpression"})
public class Scrypt {

    private final Mac mac;
    private final byte[] H = new byte[32];
    private final byte[] Q = new byte[128 + 4];
    private final int[] E = new int[32];
    private final int[] C = new int[32 * 1024];

    public byte[] hash(final byte input[]) {
        int i, j, k;
        System.arraycopy(input, 0, Q, 0, input.length);
        try {
            mac.init(new SecretKeySpec(Q, 0, 40, "HmacSHA256"));
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
        Q[40] = 0;
        Q[41] = 0;
        Q[42] = 0;
        for (i = 0; i < 4; i++) {
            Q[43] = (byte) (i + 1);
            mac.update(Q, 0, 44);
            try {
                mac.doFinal(H, 0);
            } catch (ShortBufferException e) {
                throw new IllegalStateException(e);
            }

            for (j = 0; j < 8; j++) {
                E[i * 8 + j] = (H[j * 4 + 0] & 0xff) << 0
                        | (H[j * 4 + 1] & 0xff) << 8
                        | (H[j * 4 + 2] & 0xff) << 16
                        | (H[j * 4 + 3] & 0xff) << 24;
            }
        }

        for (i = 0; i < 1024; i++) {
            System.arraycopy(E, 0, C, i * 32, 32);
            ECSalsa8(0, 16);
            ECSalsa8(16, 0);
        }
        for (i = 0; i < 1024; i++) {
            k = (E[16] & 1023) * 32;
            for (j = 0; j < 32; j++)
                E[j] ^= C[k + j];
            ECSalsa8(0, 16);
            ECSalsa8(16, 0);
        }

        for (i = 0; i < 32; i++) {
            Q[i * 4 + 0] = (byte) (E[i] >> 0);
            Q[i * 4 + 1] = (byte) (E[i] >> 8);
            Q[i * 4 + 2] = (byte) (E[i] >> 16);
            Q[i * 4 + 3] = (byte) (E[i] >> 24);
        }

        Q[128 + 3] = 1;
        mac.update(Q, 0, 128 + 4);
        try {
            mac.doFinal(H, 0);
        } catch (ShortBufferException e) {
            throw new IllegalStateException(e);
        }

        return H;
    }

    private void ECSalsa8(int di, int xi) {
        int x00 = (E[di + 0] ^= E[xi + 0]);
        int x01 = (E[di + 1] ^= E[xi + 1]);
        int x02 = (E[di + 2] ^= E[xi + 2]);
        int x03 = (E[di + 3] ^= E[xi + 3]);
        int x04 = (E[di + 4] ^= E[xi + 4]);
        int x05 = (E[di + 5] ^= E[xi + 5]);
        int x06 = (E[di + 6] ^= E[xi + 6]);
        int x07 = (E[di + 7] ^= E[xi + 7]);
        int x08 = (E[di + 8] ^= E[xi + 8]);
        int x09 = (E[di + 9] ^= E[xi + 9]);
        int x10 = (E[di + 10] ^= E[xi + 10]);
        int x11 = (E[di + 11] ^= E[xi + 11]);
        int x12 = (E[di + 12] ^= E[xi + 12]);
        int x13 = (E[di + 13] ^= E[xi + 13]);
        int x14 = (E[di + 14] ^= E[xi + 14]);
        int x15 = (E[di + 15] ^= E[xi + 15]);
        for (int i = 0; i < 8; i += 2) {
            x04 ^= Integer.rotateLeft(x00 + x12, 7);
            x08 ^= Integer.rotateLeft(x04 + x00, 9);
            x12 ^= Integer.rotateLeft(x08 + x04, 13);
            x00 ^= Integer.rotateLeft(x12 + x08, 18);
            x09 ^= Integer.rotateLeft(x05 + x01, 7);
            x13 ^= Integer.rotateLeft(x09 + x05, 9);
            x01 ^= Integer.rotateLeft(x13 + x09, 13);
            x05 ^= Integer.rotateLeft(x01 + x13, 18);
            x14 ^= Integer.rotateLeft(x10 + x06, 7);
            x02 ^= Integer.rotateLeft(x14 + x10, 9);
            x06 ^= Integer.rotateLeft(x02 + x14, 13);
            x10 ^= Integer.rotateLeft(x06 + x02, 18);
            x03 ^= Integer.rotateLeft(x15 + x11, 7);
            x07 ^= Integer.rotateLeft(x03 + x15, 9);
            x11 ^= Integer.rotateLeft(x07 + x03, 13);
            x15 ^= Integer.rotateLeft(x11 + x07, 18);
            x01 ^= Integer.rotateLeft(x00 + x03, 7);
            x02 ^= Integer.rotateLeft(x01 + x00, 9);
            x03 ^= Integer.rotateLeft(x02 + x01, 13);
            x00 ^= Integer.rotateLeft(x03 + x02, 18);
            x06 ^= Integer.rotateLeft(x05 + x04, 7);
            x07 ^= Integer.rotateLeft(x06 + x05, 9);
            x04 ^= Integer.rotateLeft(x07 + x06, 13);
            x05 ^= Integer.rotateLeft(x04 + x07, 18);
            x11 ^= Integer.rotateLeft(x10 + x09, 7);
            x08 ^= Integer.rotateLeft(x11 + x10, 9);
            x09 ^= Integer.rotateLeft(x08 + x11, 13);
            x10 ^= Integer.rotateLeft(x09 + x08, 18);
            x12 ^= Integer.rotateLeft(x15 + x14, 7);
            x13 ^= Integer.rotateLeft(x12 + x15, 9);
            x14 ^= Integer.rotateLeft(x13 + x12, 13);
            x15 ^= Integer.rotateLeft(x14 + x13, 18);
        }
        E[di + 0] += x00;
        E[di + 1] += x01;
        E[di + 2] += x02;
        E[di + 3] += x03;
        E[di + 4] += x04;
        E[di + 5] += x05;
        E[di + 6] += x06;
        E[di + 7] += x07;
        E[di + 8] += x08;
        E[di + 9] += x09;
        E[di + 10] += x10;
        E[di + 11] += x11;
        E[di + 12] += x12;
        E[di + 13] += x13;
        E[di + 14] += x14;
        E[di + 15] += x15;
    }

    {
        try {
            mac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
