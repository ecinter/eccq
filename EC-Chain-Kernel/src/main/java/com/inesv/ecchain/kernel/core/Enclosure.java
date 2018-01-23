package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.EcTime;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

public interface Enclosure {

    static boolean hasEnclosure(String enclosureName, JSONObject attachmentData) {
        return attachmentData.get("version." + enclosureName) != null;
    }

    int getSize();

    int getFullSize();

    void putBytes(ByteBuffer buffer);

    JSONObject getJSONObject();

    byte getEcVersion();

    int getBaselineFeeHeight();

    Fee getBaselineFee(Transaction transaction);

    int getNextFeeHeight();

    Fee getNextFee(Transaction transaction);

    boolean isPhased(Transaction transaction);

    abstract class AbstractEncryptedMessage extends AbstractEnclosure {

        private static final Fee ENCRYPTED_MESSAGE_FEE = new Fee.SizeBasedFee(Constants.ONE_EC, Constants.ONE_EC, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure appendage) {
                return ((AbstractEncryptedMessage) appendage).getEncryptedDataLength() - 16;
            }
        };
        private final boolean isText;
        private final boolean isCompressed;
        private EncryptedData encryptedData;

        private AbstractEncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            super(buffer, transactionVersion);
            int length = buffer.getInt();
            this.isText = length < 0;
            if (length < 0) {
                length &= Integer.MAX_VALUE;
            }
            this.encryptedData = EncryptedData.readEcEncryptedData(buffer, length, 1000);
            this.isCompressed = getEcVersion() != 2;
        }

        private AbstractEncryptedMessage(JSONObject attachmentJSON, JSONObject encryptedMessageJSON) {
            super(attachmentJSON);
            byte[] data = Convert.parseHexString((String) encryptedMessageJSON.get("data"));
            byte[] nonce = Convert.parseHexString((String) encryptedMessageJSON.get("nonce"));
            this.encryptedData = new EncryptedData(data, nonce);
            this.isText = Boolean.TRUE.equals(encryptedMessageJSON.get("isText"));
            Object isCompressed = encryptedMessageJSON.get("isCompressed");
            this.isCompressed = isCompressed == null || Boolean.TRUE.equals(isCompressed);
        }

        private AbstractEncryptedMessage(EncryptedData encryptedData, boolean isText, boolean isCompressed) {
            super(isCompressed ? 1 : 2);
            this.encryptedData = encryptedData;
            this.isText = isText;
            this.isCompressed = isCompressed;
        }

        @Override
        int getMySize() {
            return 4 + encryptedData.getSize();
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(isText ? (encryptedData.getData().length | Integer.MIN_VALUE) : encryptedData.getData().length);
            buffer.put(encryptedData.getData());
            buffer.put(encryptedData.getNonce());
        }

        @Override
        void putMyJSON(JSONObject json) {
            json.put("data", Convert.toHexString(encryptedData.getData()));
            json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
            json.put("isText", isText);
            json.put("isCompressed", isCompressed);
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return ENCRYPTED_MESSAGE_FEE;
        }

        @Override
        void validate(Transaction transaction) throws EcValidationException {
            if (EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK && getEncryptedDataLength() > Constants.EC_MAX_ENCRYPTED_MESSAGE_LENGTH) {
                throw new EcNotValidExceptionEc("Max encrypted message length exceeded");
            }
            if (encryptedData != null) {
                if ((encryptedData.getNonce().length != 32 && encryptedData.getData().length > 0)
                        || (encryptedData.getNonce().length != 0 && encryptedData.getData().length == 0)) {
                    throw new EcNotValidExceptionEc("Invalid nonce length " + encryptedData.getNonce().length);
                }
            }
            if ((getEcVersion() != 2 && !isCompressed) || (getEcVersion() == 2 && isCompressed)) {
                throw new EcNotValidExceptionEc("Version mismatch - version " + getEcVersion() + ", isCompressed " + isCompressed);
            }
        }

        @Override
        final boolean verifyVersion(byte transactionVersion) {
            return transactionVersion == 0 ? getEcVersion() == 0 : (getEcVersion() == 1 || getEcVersion() == 2);
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
        }

        public final EncryptedData getEncryptedData() {
            return encryptedData;
        }

        final void setEncryptedData(EncryptedData encryptedData) {
            this.encryptedData = encryptedData;
        }

        int getEncryptedDataLength() {
            return encryptedData.getData().length;
        }

        public final boolean isText() {
            return isText;
        }

        public final boolean isCompressed() {
            return isCompressed;
        }

        @Override
        final boolean isPhasable() {
            return false;
        }

    }

    class PrunableEncryptedMessage extends AbstractEnclosure implements Prunable {

        private static final String appendixName = "PrunableEncryptedMessage";

        private static final Fee PRUNABLE_ENCRYPTED_DATA_FEE = new Fee.SizeBasedFee(Constants.ONE_EC / 10) {
            @Override
            public int getSize(TransactionImpl transaction, Enclosure enclosure) {
                return enclosure.getFullSize();
            }
        };
        private final byte[] hash;
        private final boolean isText;
        private final boolean isCompressed;
        private EncryptedData encryptedData;
        private volatile PrunableMessage prunableMessage;
        PrunableEncryptedMessage(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.hash = new byte[32];
            buffer.get(this.hash);
            this.encryptedData = null;
            this.isText = false;
            this.isCompressed = false;
        }

        private PrunableEncryptedMessage(JSONObject attachmentJSON) {
            super(attachmentJSON);
            String hashString = Convert.emptyToNull((String) attachmentJSON.get("encryptedMessageHash"));
            JSONObject encryptedMessageJSON = (JSONObject) attachmentJSON.get("encryptedMessage");
            if (hashString != null && encryptedMessageJSON == null) {
                this.hash = Convert.parseHexString(hashString);
                this.encryptedData = null;
                this.isText = false;
                this.isCompressed = false;
            } else {
                this.hash = null;
                byte[] data = Convert.parseHexString((String) encryptedMessageJSON.get("data"));
                byte[] nonce = Convert.parseHexString((String) encryptedMessageJSON.get("nonce"));
                this.encryptedData = new EncryptedData(data, nonce);
                this.isText = Boolean.TRUE.equals(encryptedMessageJSON.get("isText"));
                this.isCompressed = Boolean.TRUE.equals(encryptedMessageJSON.get("isCompressed"));
            }
        }

        public PrunableEncryptedMessage(EncryptedData encryptedData, boolean isText, boolean isCompressed) {
            this.encryptedData = encryptedData;
            this.isText = isText;
            this.isCompressed = isCompressed;
            this.hash = null;
        }

        static PrunableEncryptedMessage parse(JSONObject attachmentData) {
            if (!hasEnclosure(appendixName, attachmentData)) {
                return null;
            }
            JSONObject encryptedMessageJSON = (JSONObject) attachmentData.get("encryptedMessage");
            if (encryptedMessageJSON != null && encryptedMessageJSON.get("data") == null) {
                return new UnencryptedPrunableEncryptedMessage(attachmentData);
            }
            return new PrunableEncryptedMessage(attachmentData);
        }

        @Override
        public final Fee getBaselineFee(Transaction transaction) {
            return PRUNABLE_ENCRYPTED_DATA_FEE;
        }

        @Override
        final int getMySize() {
            return 32;
        }

        @Override
        final int getMyFullSize() {
            return getEncryptedDataLength();
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put(getHash());
        }

        @Override
        void putMyJSON(JSONObject json) {
            if (prunableMessage != null) {
                JSONObject encryptedMessageJSON = new JSONObject();
                json.put("encryptedMessage", encryptedMessageJSON);
                encryptedMessageJSON.put("data", Convert.toHexString(prunableMessage.getEncryptedData().getData()));
                encryptedMessageJSON.put("nonce", Convert.toHexString(prunableMessage.getEncryptedData().getNonce()));
                encryptedMessageJSON.put("isText", prunableMessage.encryptedMessageIsText());
                encryptedMessageJSON.put("isCompressed", prunableMessage.isCompressed());
            } else if (encryptedData != null) {
                JSONObject encryptedMessageJSON = new JSONObject();
                json.put("encryptedMessage", encryptedMessageJSON);
                encryptedMessageJSON.put("data", Convert.toHexString(encryptedData.getData()));
                encryptedMessageJSON.put("nonce", Convert.toHexString(encryptedData.getNonce()));
                encryptedMessageJSON.put("isText", isText);
                encryptedMessageJSON.put("isCompressed", isCompressed);
            }
            json.put("encryptedMessageHash", Convert.toHexString(getHash()));
        }

        @Override
        final String getAppendixName() {
            return appendixName;
        }

        @Override
        void validate(Transaction transaction) throws EcValidationException {
            if (transaction.getEncryptedMessage() != null) {
                throw new EcNotValidExceptionEc("Cannot have both encrypted and prunable encrypted message attachments");
            }
            EncryptedData ed = getEncryptedData();
            if (ed == null && new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MIN_PRUNABLE_LIFETIME) {
                throw new EcNotCurrentlyValidExceptionEc("Encrypted message has been pruned prematurely");
            }
            if (ed != null) {
                if (ed.getData().length > Constants.EC_MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH) {
                    throw new EcNotValidExceptionEc(String.format("Message length %d exceeds max prunable encrypted message length %d",
                            ed.getData().length, Constants.EC_MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH));
                }
                if ((ed.getNonce().length != 32 && ed.getData().length > 0)
                        || (ed.getNonce().length != 0 && ed.getData().length == 0)) {
                    throw new EcNotValidExceptionEc("Invalid nonce length " + ed.getNonce().length);
                }
            }
            if (transaction.getRecipientId() == 0) {
                throw new EcNotValidExceptionEc("Encrypted messages cannot be attached to transactions with no recipient");
            }
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MAX_PRUNABLE_LIFETIME) {
                PrunableMessage.add((TransactionImpl) transaction, this);
            }
        }

        public final EncryptedData getEncryptedData() {
            if (prunableMessage != null) {
                return prunableMessage.getEncryptedData();
            }
            return encryptedData;
        }

        final void setEncryptedData(EncryptedData encryptedData) {
            this.encryptedData = encryptedData;
        }

        int getEncryptedDataLength() {
            return getEncryptedData() == null ? 0 : getEncryptedData().getData().length;
        }

        public final boolean isText() {
            if (prunableMessage != null) {
                return prunableMessage.encryptedMessageIsText();
            }
            return isText;
        }

        public final boolean isCompressed() {
            if (prunableMessage != null) {
                return prunableMessage.isCompressed();
            }
            return isCompressed;
        }

        @Override
        public final byte[] getHash() {
            if (hash != null) {
                return hash;
            }
            MessageDigest digest = Crypto.sha256();
            digest.update((byte) (isText ? 1 : 0));
            digest.update((byte) (isCompressed ? 1 : 0));
            digest.update(encryptedData.getData());
            digest.update(encryptedData.getNonce());
            return digest.digest();
        }

        @Override
        void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
            if (!hasPrunableData() && shouldLoadPrunable(transaction, includeExpiredPrunable)) {
                PrunableMessage prunableMessage = PrunableMessage.getPrunableMessage(transaction.getTransactionId());
                if (prunableMessage != null && prunableMessage.getEncryptedData() != null) {
                    this.prunableMessage = prunableMessage;
                }
            }
        }

        @Override
        final boolean isPhasable() {
            return false;
        }

        @Override
        public final boolean hasPrunableData() {
            return (prunableMessage != null || encryptedData != null);
        }

        @Override
        public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
            PrunableMessage.add((TransactionImpl) transaction, this, blockTimestamp, height);
        }
    }

    final class UnencryptedPrunableEncryptedMessage extends PrunableEncryptedMessage implements Encryptable {

        private final byte[] messageToEncrypt;
        private final byte[] recipientPublicKey;

        private UnencryptedPrunableEncryptedMessage(JSONObject attachmentJSON) {
            super(attachmentJSON);
            setEncryptedData(null);
            JSONObject encryptedMessageJSON = (JSONObject) attachmentJSON.get("encryptedMessage");
            String messageToEncryptString = (String) encryptedMessageJSON.get("messageToEncrypt");
            this.messageToEncrypt = isText() ? Convert.toBytes(messageToEncryptString) : Convert.parseHexString(messageToEncryptString);
            this.recipientPublicKey = Convert.parseHexString((String) attachmentJSON.get("recipientPublicKey"));
        }

        public UnencryptedPrunableEncryptedMessage(byte[] messageToEncrypt, boolean isText, boolean isCompressed, byte[] recipientPublicKey) {
            super(null, isText, isCompressed);
            this.messageToEncrypt = messageToEncrypt;
            this.recipientPublicKey = recipientPublicKey;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            if (getEncryptedData() == null) {
                throw new EcNotYetEncryptedException("Prunable encrypted message not yet encrypted");
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
                json.put("recipientPublicKey", Convert.toHexString(recipientPublicKey));
                json.put("encryptedMessage", encryptedMessageJSON);
            } else {
                super.putMyJSON(json);
            }
        }

        @Override
        void validate(Transaction transaction) throws EcValidationException {
            if (getEncryptedData() == null) {
                int dataLength = getEncryptedDataLength();
                if (dataLength > Constants.EC_MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH) {
                    throw new EcNotValidExceptionEc(String.format("Message length %d exceeds max prunable encrypted message length %d",
                            dataLength, Constants.EC_MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH));
                }
            } else {
                super.validate(transaction);
            }
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (getEncryptedData() == null) {
                throw new EcNotYetEncryptedException("Prunable encrypted message not yet encrypted");
            }
            super.apply(transaction, senderAccount, recipientAccount);
        }

        @Override
        void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
        }

        @Override
        public void encrypt(String secretPhrase) {
            setEncryptedData(EncryptedData.encrypt(getPlaintext(), secretPhrase, recipientPublicKey));
        }

        @Override
        int getEncryptedDataLength() {
            return EncryptedData.getEcEncryptedDataLength(getPlaintext());
        }

        private byte[] getPlaintext() {
            return isCompressed() && messageToEncrypt.length > 0 ? Convert.compress(messageToEncrypt) : messageToEncrypt;
        }

    }

    class EncryptedMessage extends AbstractEncryptedMessage {

        private static final String appendixName = "EncryptedMessage";

        EncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            super(buffer, transactionVersion);
        }

        EncryptedMessage(JSONObject attachmentData) {
            super(attachmentData, (JSONObject) attachmentData.get("encryptedMessage"));
        }

        public EncryptedMessage(EncryptedData encryptedData, boolean isText, boolean isCompressed) {
            super(encryptedData, isText, isCompressed);
        }

        static EncryptedMessage parse(JSONObject attachmentData) {
            if (!hasEnclosure(appendixName, attachmentData)) {
                return null;
            }
            if (((JSONObject) attachmentData.get("encryptedMessage")).get("data") == null) {
                return new UnencryptedEncryptedMessage(attachmentData);
            }
            return new EncryptedMessage(attachmentData);
        }

        @Override
        final String getAppendixName() {
            return appendixName;
        }

        @Override
        void putMyJSON(JSONObject json) {
            JSONObject encryptedMessageJSON = new JSONObject();
            super.putMyJSON(encryptedMessageJSON);
            json.put("encryptedMessage", encryptedMessageJSON);
        }

        @Override
        void validate(Transaction transaction) throws EcValidationException {
            super.validate(transaction);
            if (transaction.getRecipientId() == 0) {
                throw new EcNotValidExceptionEc("Encrypted messages cannot be attached to transactions with no recipient");
            }
        }

    }

    class EncryptToSelfMessage extends AbstractEncryptedMessage {

        private static final String appendixName = "EncryptToSelfMessage";

        EncryptToSelfMessage(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            super(buffer, transactionVersion);
        }

        EncryptToSelfMessage(JSONObject attachmentData) {
            super(attachmentData, (JSONObject) attachmentData.get("encryptToSelfMessage"));
        }

        public EncryptToSelfMessage(EncryptedData encryptedData, boolean isText, boolean isCompressed) {
            super(encryptedData, isText, isCompressed);
        }

        static EncryptToSelfMessage parse(JSONObject attachmentData) {
            if (!hasEnclosure(appendixName, attachmentData)) {
                return null;
            }
            if (((JSONObject) attachmentData.get("encryptToSelfMessage")).get("data") == null) {
                return new UnencryptedEncryptToSelfMessage(attachmentData);
            }
            return new EncryptToSelfMessage(attachmentData);
        }

        @Override
        final String getAppendixName() {
            return appendixName;
        }

        @Override
        void putMyJSON(JSONObject json) {
            JSONObject encryptToSelfMessageJSON = new JSONObject();
            super.putMyJSON(encryptToSelfMessageJSON);
            json.put("encryptToSelfMessage", encryptToSelfMessageJSON);
        }

    }

}
