package com.inesv.ecchain.kernel.peer;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;

public final class QualityProof {

    private final String hallmarkString;
    private final int port;
    private final int weight;
    private final int date;
    private final String host;
    private final byte[] publicKey;
    private final long accountId;
    private final byte[] signature;
    private final boolean isValid;
    private QualityProof(String hallmarkString, byte[] publicKey, byte[] signature, String host, int weight, int date, boolean isValid)
            throws URISyntaxException {
        this.hallmarkString = hallmarkString;
        URI uri = new URI("http://" + host);
        this.host = uri.getHost();
        this.port = uri.getPort() == -1 ? Peers.getDefaultPeerPort() : uri.getPort();
        this.publicKey = publicKey;
        this.accountId = Account.getId(publicKey);
        this.signature = signature;
        this.weight = weight;
        this.date = date;
        this.isValid = isValid;
    }

    public static int parseDate(String dateValue) {
        return Integer.parseInt(dateValue.substring(0, 4)) * 10000
                + Integer.parseInt(dateValue.substring(5, 7)) * 100
                + Integer.parseInt(dateValue.substring(8, 10));
    }

    public static String formatDate(int date) {
        int year = date / 10000;
        int month = (date % 10000) / 100;
        int day = date % 100;
        return (year < 10 ? "000" : (year < 100 ? "00" : (year < 1000 ? "0" : ""))) + year + "-" + (month < 10 ? "0" : "") + month + "-" + (day < 10 ? "0" : "") + day;
    }

    public static String generateHallmark(String secretPhrase, String host, int weight, int date) {

        if (host.length() == 0 || host.length() > 100) {
            throw new IllegalArgumentException("Hostname length should be between 1 and 100");
        }
        if (weight <= 0 || weight > Constants.MAX_BALANCE_EC) {
            throw new IllegalArgumentException("Weight should be between 1 and " + Constants.MAX_BALANCE_EC);
        }

        byte[] publicKey = Crypto.getPublicKey(secretPhrase);
        byte[] hostBytes = Convert.toBytes(host);

        ByteBuffer buffer = ByteBuffer.allocate(32 + 2 + hostBytes.length + 4 + 4 + 1);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(publicKey);
        buffer.putShort((short) hostBytes.length);
        buffer.put(hostBytes);
        buffer.putInt(weight);
        buffer.putInt(date);

        byte[] data = buffer.array();
        data[data.length - 1] = (byte) ThreadLocalRandom.current().nextInt();
        byte[] signature = Crypto.ecSign(data, secretPhrase);

        return Convert.toHexString(data) + Convert.toHexString(signature);

    }

    public static QualityProof parseHallmark(String hallmarkString) {

        hallmarkString = hallmarkString.trim();
        if (hallmarkString.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hallmark string length " + hallmarkString.length());
        }

        byte[] hallmarkBytes = Convert.parseHexString(hallmarkString);

        ByteBuffer buffer = ByteBuffer.wrap(hallmarkBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] publicKey = new byte[32];
        buffer.get(publicKey);
        int hostLength = buffer.getShort();
        if (hostLength > 300) {
            throw new IllegalArgumentException("Invalid host length");
        }
        byte[] hostBytes = new byte[hostLength];
        buffer.get(hostBytes);
        String host = Convert.toString(hostBytes);
        int weight = buffer.getInt();
        int date = buffer.getInt();
        buffer.get();
        byte[] signature = new byte[64];
        buffer.get(signature);

        byte[] data = new byte[hallmarkBytes.length - 64];
        System.arraycopy(hallmarkBytes, 0, data, 0, data.length);

        boolean isValid = host.length() < 100 && weight > 0 && weight <= Constants.MAX_BALANCE_EC
                && Crypto.ecVerify(signature, data, publicKey, true);
        try {
            return new QualityProof(hallmarkString, publicKey, signature, host, weight, date, isValid);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.toString(), e);
        }

    }

    public String getHallmarkString() {
        return hallmarkString;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getWeight() {
        return weight;
    }

    public int getDate() {
        return date;
    }

    public byte[] getSignature() {
        return signature;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getAccountId() {
        return accountId;
    }

    public boolean isValid() {
        return isValid;
    }

}
