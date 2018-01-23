package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.crypto.HashFunction;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class CoinMinting {

    public static final Set<HashFunction> ACCEPTED_HASH_FUNCTIONS =
            Collections.unmodifiableSet(EnumSet.of(HashFunction.SHA256, HashFunction.SHA3, HashFunction.SCRYPT, HashFunction.Keccak25));

    public static boolean meetsTarget(long accountId, Coin coin, Mortgaged.MonetarySystemCurrencyMinting attachment) {
        byte[] hash = getHash(coin.getAlgorithm(), attachment.getNonce(), attachment.getCurrencyId(), attachment.getUnits(),
                attachment.getCounter(), accountId);
        byte[] target = getTarget(coin.getMinDifficulty(), coin.getMaxDifficulty(),
                attachment.getUnits(), coin.getCurrentSupply() - coin.getReserveSupply(), coin.getMaxSupply() - coin.getReserveSupply());
        return meetsTarget(hash, target);
    }

    public static boolean meetsTarget(byte[] hash, byte[] target) {
        for (int i = hash.length - 1; i >= 0; i--) {
            if ((hash[i] & 0xff) > (target[i] & 0xff)) {
                return false;
            }
            if ((hash[i] & 0xff) < (target[i] & 0xff)) {
                return true;
            }
        }
        return true;
    }

    public static byte[] getHash(byte algorithm, long nonce, long currencyId, long units, long counter, long accountId) {
        HashFunction hashFunction = HashFunction.getHashFunction(algorithm);
        return getHash(hashFunction, nonce, currencyId, units, counter, accountId);
    }

    public static byte[] getHash(HashFunction hashFunction, long nonce, long currencyId, long units, long counter, long accountId) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8 + 8 + 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(nonce);
        buffer.putLong(currencyId);
        buffer.putLong(units);
        buffer.putLong(counter);
        buffer.putLong(accountId);
        return hashFunction.hash(buffer.array());
    }

    public static byte[] getTarget(int min, int max, long units, long currentMintableSupply, long totalMintableSupply) {
        return getTarget(getNumericTarget(min, max, units, currentMintableSupply, totalMintableSupply));
    }

    public static byte[] getTarget(BigInteger numericTarget) {
        byte[] targetRowBytes = numericTarget.toByteArray();
        if (targetRowBytes.length == 32) {
            return reverse(targetRowBytes);
        }
        byte[] targetBytes = new byte[32];
        Arrays.fill(targetBytes, 0, 32 - targetRowBytes.length, (byte) 0);
        System.arraycopy(targetRowBytes, 0, targetBytes, 32 - targetRowBytes.length, targetRowBytes.length);
        return reverse(targetBytes);
    }

    public static BigInteger getNumericTarget(Coin coin, long units) {
        return getNumericTarget(coin.getMinDifficulty(), coin.getMaxDifficulty(), units,
                coin.getCurrentSupply() - coin.getReserveSupply(), coin.getMaxSupply() - coin.getReserveSupply());
    }

    public static BigInteger getNumericTarget(int min, int max, long units, long currentMintableSupply, long totalMintableSupply) {
        if (min < 1 || max > 255) {
            throw new IllegalArgumentException(String.format("Min: %d, Max: %d, allowed range is 1 to 255", min, max));
        }
        int exp = (int) (256 - min - ((max - min) * currentMintableSupply) / totalMintableSupply);
        return BigInteger.valueOf(2).pow(exp).subtract(BigInteger.ONE).divide(BigInteger.valueOf(units));
    }

    private static byte[] reverse(byte[] b) {
        for (int i = 0; i < b.length / 2; i++) {
            byte temp = b[i];
            b[i] = b[b.length - i - 1];
            b[b.length - i - 1] = temp;
        }
        return b;
    }

}
