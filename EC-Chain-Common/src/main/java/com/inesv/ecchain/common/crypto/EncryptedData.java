

package com.inesv.ecchain.common.crypto;


import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.util.Convert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class EncryptedData {

    private final byte[] data;

    private final byte[] nonce;

    public static final EncryptedData EMPTY_DATA = new EncryptedData(new byte[0], new byte[0]);

    public static EncryptedData encrypt(byte[] plaintext, String secretPhrase, byte[] theirPublicKey) {
        if (plaintext.length == 0) {
            return EMPTY_DATA;
        }
        byte[] nonce = new byte[32];
        Crypto.getEcSecureRandom().nextBytes(nonce);
        byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), theirPublicKey, nonce);
        byte[] data = Crypto.aesEncrypt(plaintext, sharedKey);
        return new EncryptedData(data, nonce);
    }

    public static EncryptedData readEcEncryptedData(ByteBuffer buffer, int length, int maxLength)
            throws EcNotValidExceptionEc {
        if (length == 0) {
            return EMPTY_DATA;
        }
        if (length > maxLength) {
            throw new EcNotValidExceptionEc("Max encrypted data length exceeded: " + length);
        }
        byte[] data = new byte[length];
        buffer.get(data);
        byte[] nonce = new byte[32];
        buffer.get(nonce);
        return new EncryptedData(data, nonce);
    }

    public static EncryptedData readEncryptedData(byte[] bytes) {
        if (bytes.length == 0) {
            return EMPTY_DATA;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        try {
            return readEcEncryptedData(buffer, bytes.length - 32, Integer.MAX_VALUE);
        } catch (EcNotValidExceptionEc e) {
            throw new RuntimeException(e.toString(), e); // never
        }
    }

    public static int getEcEncryptedDataLength(byte[] plaintext) {
        if (plaintext.length == 0) {
            return 0;
        }
        return Crypto.aesEncrypt(plaintext, new byte[32]).length;
    }

    public static int getEncryptedSize(byte[] plaintext) {
        if (plaintext.length == 0) {
            return 0;
        }
        return getEcEncryptedDataLength(plaintext) + 32;
    }

    public EncryptedData(byte[] data, byte[] nonce) {
        this.data = data;
        this.nonce = nonce;
    }

    public byte[] decrypt(String secretPhrase, byte[] theirPublicKey) {
        if (data.length == 0) {
            return data;
        }
        byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), theirPublicKey, nonce);
        return Crypto.aesDecrypt(data, sharedKey);
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public int getSize() {
        return data.length + nonce.length;
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(nonce.length + data.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(data);
        buffer.put(nonce);
        return buffer.array();
    }

    @Override
    public String toString() {
        return "data: " + Convert.toHexString(data) + " nonce: " + Convert.toHexString(nonce);
    }

}
