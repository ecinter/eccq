package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcValidationException;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

/**
 * @Author:Lin
 * @Description:
 * @Date:20:12 2017/12/21
 * @Modified by:
 */
public abstract class AbstractEnclosure implements Enclosure {

    private final byte version;

    AbstractEnclosure(JSONObject attachmentData) {
        Long l = (Long) attachmentData.get("version." + getAppendixName());
        version = (byte) (l == null ? 0 : l);
    }

    AbstractEnclosure(ByteBuffer buffer, byte transactionVersion) {
        if (transactionVersion == 0) {
            version = 0;
        } else {
            version = buffer.get();
        }
    }

    AbstractEnclosure(int version) {
        this.version = (byte) version;
    }

    AbstractEnclosure() {
        this.version = 1;
    }

    abstract String getAppendixName();

    @Override
    public final int getSize() {
        return getMySize() + (version > 0 ? 1 : 0);
    }

    @Override
    public final int getFullSize() {
        return getMyFullSize() + (version > 0 ? 1 : 0);
    }

    abstract int getMySize();

    int getMyFullSize() {
        return getMySize();
    }

    @Override
    public final void putBytes(ByteBuffer buffer) {
        if (version > 0) {
            buffer.put(version);
        }
        putMyBytes(buffer);
    }

    abstract void putMyBytes(ByteBuffer buffer);

    @Override
    public final JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("version." + getAppendixName(), version);
        putMyJSON(json);
        return json;
    }

    abstract void putMyJSON(JSONObject json);

    @Override
    public final byte getEcVersion() {
        return version;
    }

    boolean verifyVersion(byte transactionVersion) {
        return transactionVersion == 0 ? version == 0 : version == 1;
    }

    @Override
    public int getBaselineFeeHeight() {
        return Constants.EC_SHUFFLING_BLOCK;
    }

    @Override
    public Fee getBaselineFee(Transaction transaction) {
        return Fee.NONE;
    }

    @Override
    public int getNextFeeHeight() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Fee getNextFee(Transaction transaction) {
        return getBaselineFee(transaction);
    }

    abstract void validate(Transaction transaction) throws EcValidationException;

    void validateAtFinish(Transaction transaction) throws EcValidationException {
        if (!isPhased(transaction)) {
            return;
        }
        validate(transaction);
    }

    abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount);

    final void loadPrunable(Transaction transaction) {
        loadPrunable(transaction, false);
    }

    void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
    }

    abstract boolean isPhasable();

    @Override
    public final boolean isPhased(Transaction transaction) {
        return isPhasable() && transaction.getPhasing() != null;
    }

}
