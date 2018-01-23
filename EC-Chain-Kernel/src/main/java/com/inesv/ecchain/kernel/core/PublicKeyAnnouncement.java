package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;


public final class PublicKeyAnnouncement extends AbstractEnclosure {

    private static final String appendixName = "PublicKeyAnnouncement";
    private final byte[] publicKey;

    PublicKeyAnnouncement(ByteBuffer buffer, byte transactionVersion) {
        super(buffer, transactionVersion);
        this.publicKey = new byte[32];
        buffer.get(this.publicKey);
    }

    PublicKeyAnnouncement(JSONObject attachmentData) {
        super(attachmentData);
        this.publicKey = Convert.parseHexString((String) attachmentData.get("recipientPublicKey"));
    }

    public PublicKeyAnnouncement(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    static com.inesv.ecchain.kernel.core.PublicKeyAnnouncement parse(JSONObject attachmentData) {
        if (!Enclosure.hasEnclosure(appendixName, attachmentData)) {
            return null;
        }
        return new com.inesv.ecchain.kernel.core.PublicKeyAnnouncement(attachmentData);
    }

    @Override
    String getAppendixName() {
        return appendixName;
    }

    @Override
    int getMySize() {
        return 32;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
        buffer.put(publicKey);
    }

    @Override
    void putMyJSON(JSONObject json) {
        json.put("recipientPublicKey", Convert.toHexString(publicKey));
    }

    @Override
    void validate(Transaction transaction) throws EcValidationException {
        if (transaction.getRecipientId() == 0) {
            throw new EcNotValidExceptionEc("PublicKeyAnnouncement cannot be attached to transactions with no recipient");
        }
        if (!Crypto.isCanonicalPublicKey(publicKey)) {
            throw new EcNotValidExceptionEc("Invalid recipient public key: " + Convert.toHexString(publicKey));
        }
        long recipientId = transaction.getRecipientId();
        if (Account.getId(this.publicKey) != recipientId) {
            throw new EcNotValidExceptionEc("Announced public key does not match recipient accountId");
        }
        byte[] recipientPublicKey = Account.getPublicKey(recipientId);
        if (recipientPublicKey != null && !Arrays.equals(publicKey, recipientPublicKey)) {
            throw new EcNotCurrentlyValidExceptionEc("A different public key for this account has already been announced");
        }
    }

    @Override
    void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
        if (Account.setOrVerify(recipientAccount.getId(), publicKey)) {
            recipientAccount.apply(this.publicKey);
        }
    }

    @Override
    boolean isPhasable() {
        return false;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

}
