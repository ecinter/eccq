package com.inesv.ecchain.kernel.core;

import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.util.List;

public interface EcBlock {

    int getECVersion();

    long getECId();

    String getStringECId();

    int getHeight();

    int getTimestamp();

    long getFoundryId();

    byte[] getFoundryPublicKey();

    long getPreviousBlockId();

    byte[] getPreviousBlockHash();

    long getNextBlockId();

    long getTotalAmountNQT();

    long getTotalFeeNQT();

    int getPayloadLength();

    byte[] getPayloadHash();

    List<? extends Transaction> getTransactions();

    byte[] getFoundrySignature();

    byte[] getBlockSignature();

    long getBaseTarget();

    BigInteger getCumulativeDifficulty();

    byte[] getBytes();

    JSONObject getJSONObject();

}
