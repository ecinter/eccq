package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcNotYetEncryptedException;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

/**
 * @Author:Lin
 * @Description:
 * @Date:20:14 2017/12/21
 * @Modified by:
 */
public final class UnencryptedEncryptedMessage extends Enclosure.EncryptedMessage implements Encryptable {

    private final byte[] messageToEncrypt;
    private final byte[] recipientPublicKey;

    UnencryptedEncryptedMessage(JSONObject attachmentData) {
        super(attachmentData);
        setEncryptedData(null);
        JSONObject encryptedMessageJSON = (JSONObject) attachmentData.get("encryptedMessage");
        String messageToEncryptString = (String) encryptedMessageJSON.get("messageToEncrypt");
        messageToEncrypt = isText() ? Convert.toBytes(messageToEncryptString) : Convert.parseHexString(messageToEncryptString);
        recipientPublicKey = Convert.parseHexString((String) attachmentData.get("recipientPublicKey"));
    }

    public UnencryptedEncryptedMessage(byte[] messageToEncrypt, boolean isText, boolean isCompressed, byte[] recipientPublicKey) {
        super(null, isText, isCompressed);
        this.messageToEncrypt = messageToEncrypt;
        this.recipientPublicKey = recipientPublicKey;
    }

    @Override
    int getMySize() {
        if (getEncryptedData() != null) {
            return super.getMySize();
        }
        return 4 + EncryptedData.getEncryptedSize(getPlaintext());
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
        if (getEncryptedData() == null) {
            throw new EcNotYetEncryptedException("Message not yet encrypted");
        }
        super.putMyBytes(buffer);
    }

    @Override
    void putMyJSON(JSONObject json) {
        if (getEncryptedData() == null) {
            JSONObject encryptedMessageJSON = new JSONObject();
            encryptedMessageJSON.put("messageToEncrypt", isText() ? Convert.toString(messageToEncrypt) : Convert.toHexString(messageToEncrypt));
            encryptedMessageJSON.put("isText", isText());
            encryptedMessageJSON.put("isCompressed", isCompressed());
            json.put("encryptedMessage", encryptedMessageJSON);
            json.put("recipientPublicKey", Convert.toHexString(recipientPublicKey));
        } else {
            super.putMyJSON(json);
        }
    }

    @Override
    void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
        if (getEncryptedData() == null) {
            throw new EcNotYetEncryptedException("Message not yet encrypted");
        }
        super.apply(transaction, senderAccount, recipientAccount);
    }

    @Override
    public void encrypt(String secretPhrase) {
        setEncryptedData(EncryptedData.encrypt(getPlaintext(), secretPhrase, recipientPublicKey));
    }

    private byte[] getPlaintext() {
        return isCompressed() && messageToEncrypt.length > 0 ? Convert.compress(messageToEncrypt) : messageToEncrypt;
    }

    @Override
    int getEncryptedDataLength() {
        return EncryptedData.getEcEncryptedDataLength(getPlaintext());
    }

}
