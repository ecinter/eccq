

package com.inesv.ecchain.common.crypto;


import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.util.Convert;



import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class EcAnonymouslyEncrypted {

    private final byte[] data;

    private final byte[] publicKey;

    public static EcAnonymouslyEncrypted encrypt(byte[] plaintext, String secretPhrase, byte[] theirPublicKey, byte[] nonce) {
        byte[] keySeed = Crypto.getEcKeySeed(secretPhrase, theirPublicKey, nonce);
        byte[] myPrivateKey = Crypto.getPrivateKey(keySeed);
        byte[] myPublicKey = Crypto.getPublicKey(keySeed);
        byte[] sharedKey = Crypto.getSharedKey(myPrivateKey, theirPublicKey);
        byte[] data = Crypto.aesGCMEncrypt(plaintext, sharedKey);
        return new EcAnonymouslyEncrypted(data, myPublicKey);
    }

    public static EcAnonymouslyEncrypted readEcEncryptedData(ByteBuffer buffer, int length, int maxLength)
            throws EcNotValidExceptionEc {
        if (length > maxLength) {
            throw new EcNotValidExceptionEc("Max encrypted data length exceeded: " + length);
        }
        byte[] data = new byte[length];
        buffer.get(data);
        byte[] publicKey = new byte[32];
        buffer.get(publicKey);
        return new EcAnonymouslyEncrypted(data, publicKey);
    }

    public static EcAnonymouslyEncrypted readEncryptedData(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        try {
            return readEcEncryptedData(buffer, bytes.length - 32, Integer.MAX_VALUE);
        } catch (EcNotValidExceptionEc e) {
            throw new RuntimeException(e.toString(), e); // never
        }
    }

    public EcAnonymouslyEncrypted(byte[] data, byte[] publicKey) {
        this.data = data;
        this.publicKey = publicKey;
    }

    public byte[] decrypt(String secretPhrase) {
        byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), publicKey);
        return Crypto.aesGCMDecrypt(data, sharedKey);
    }

    public byte[] decrypt(byte[] keySeed, byte[] theirPublicKey) {
        if (!Arrays.equals(Crypto.getPublicKey(keySeed), publicKey)) {
            throw new RuntimeException("Data was not encrypted using this keySeed");
        }
        byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(keySeed), theirPublicKey);
        return Crypto.aesGCMDecrypt(data, sharedKey);
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public int getSize() {
        return data.length + 32;
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 32);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(data);
        buffer.put(publicKey);
        return buffer.array();
    }

    @Override
    public String toString() {
        return "data: " + Convert.toHexString(data) + " publicKey: " + Convert.toHexString(publicKey);
    }

}
