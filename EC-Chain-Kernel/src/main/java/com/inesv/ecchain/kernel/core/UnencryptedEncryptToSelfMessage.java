package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcNotYetEncryptedException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;


public final class UnencryptedEncryptToSelfMessage extends Enclosure.EncryptToSelfMessage implements Encryptable {

    private final byte[] messageToEncrypt;

    UnencryptedEncryptToSelfMessage(JSONObject attachmentData) {
        super(attachmentData);
        setEncryptedData(null);
        JSONObject encryptedMessageJSON = (JSONObject) attachmentData.get("encryptToSelfMessage");
        String messageToEncryptString = (String) encryptedMessageJSON.get("messageToEncrypt");
        messageToEncrypt = isText() ? Convert.toBytes(messageToEncryptString) : Convert.parseHexString(messageToEncryptString);
    }

    public UnencryptedEncryptToSelfMessage(byte[] messageToEncrypt, boolean isText, boolean isCompressed) {
        super(null, isText, isCompressed);
        this.messageToEncrypt = messageToEncrypt;
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
            json.put("encryptToSelfMessage", encryptedMessageJSON);
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
        setEncryptedData(EncryptedData.encrypt(getPlaintext(), secretPhrase, Crypto.getPublicKey(secretPhrase)));
    }

    @Override
    int getEncryptedDataLength() {
        return EncryptedData.getEcEncryptedDataLength(getPlaintext());
    }

    private byte[] getPlaintext() {
        return isCompressed() && messageToEncrypt.length > 0 ? Convert.compress(messageToEncrypt) : messageToEncrypt;
    }

}
