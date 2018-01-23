package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.EcTime;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class PrunablePlainMessage extends AbstractEnclosure implements Prunable {

    private static final Fee PRUNABLE_MESSAGE_FEE = new Fee.SizeBasedFee(Constants.ONE_EC / 10) {
        @Override
        public int getSize(TransactionImpl transaction, Enclosure enclosure) {
            return enclosure.getFullSize();
        }
    };
    private final byte[] hash;
    private final byte[] message;
    private final boolean isText;
    private volatile PrunableMessage prunableMessage;

    PrunablePlainMessage(ByteBuffer buffer, byte transactionVersion) {
        super(buffer, transactionVersion);
        this.hash = new byte[32];
        buffer.get(this.hash);
        this.message = null;
        this.isText = false;
    }

    private PrunablePlainMessage(JSONObject attachmentData) {
        super(attachmentData);
        String hashString = Convert.emptyToNull((String) attachmentData.get("messageHash"));
        String messageString = Convert.emptyToNull((String) attachmentData.get("message"));
        if (hashString != null && messageString == null) {
            this.hash = Convert.parseHexString(hashString);
            this.message = null;
            this.isText = false;
        } else {
            this.hash = null;
            this.isText = Boolean.TRUE.equals(attachmentData.get("messageIsText"));
            this.message = Convert.toBytes(messageString, isText);
        }
    }

    public PrunablePlainMessage(byte[] message) {
        this(message, false);
    }

    public PrunablePlainMessage(String string) {
        this(Convert.toBytes(string), true);
    }

    public PrunablePlainMessage(String string, boolean isText) {
        this(Convert.toBytes(string, isText), isText);
    }

    public PrunablePlainMessage(byte[] message, boolean isText) {
        this.message = message;
        this.isText = isText;
        this.hash = null;
    }

    static com.inesv.ecchain.kernel.core.PrunablePlainMessage parse(JSONObject attachmentData) {
        if (!Enclosure.hasEnclosure(Constants.appendixName, attachmentData)) {
            return null;
        }
        return new com.inesv.ecchain.kernel.core.PrunablePlainMessage(attachmentData);
    }

    @Override
    String getAppendixName() {
        return Constants.appendixName;
    }

    @Override
    public Fee getBaselineFee(Transaction transaction) {
        return PRUNABLE_MESSAGE_FEE;
    }

    @Override
    int getMySize() {
        return 32;
    }

    @Override
    int getMyFullSize() {
        return getMessage() == null ? 0 : getMessage().length;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
        buffer.put(getHash());
    }

    @Override
    void putMyJSON(JSONObject json) {
        if (prunableMessage != null) {
            json.put("message", Convert.toString(prunableMessage.getMessage(), prunableMessage.messageIsText()));
            json.put("messageIsText", prunableMessage.messageIsText());
        } else if (message != null) {
            json.put("message", Convert.toString(message, isText));
            json.put("messageIsText", isText);
        }
        json.put("messageHash", Convert.toHexString(getHash()));
    }

    @Override
    void validate(Transaction transaction) throws EcValidationException {
        if (transaction.getMessage() != null) {
            throw new EcNotValidExceptionEc("Cannot have both message and prunable message attachments");
        }
        byte[] msg = getMessage();
        if (msg != null && msg.length > Constants.EC_MAX_PRUNABLE_MESSAGE_LENGTH) {
            throw new EcNotValidExceptionEc("Invalid prunable message length: " + msg.length);
        }
        if (msg == null && new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MIN_PRUNABLE_LIFETIME) {
            throw new EcNotCurrentlyValidExceptionEc("Message has been pruned prematurely");
        }
    }

    @Override
    void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
        if (new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MAX_PRUNABLE_LIFETIME) {
            PrunableMessage.add((TransactionImpl) transaction, this);
        }
    }

    public byte[] getMessage() {
        if (prunableMessage != null) {
            return prunableMessage.getMessage();
        }
        return message;
    }

    public boolean isText() {
        if (prunableMessage != null) {
            return prunableMessage.messageIsText();
        }
        return isText;
    }

    @Override
    public byte[] getHash() {
        if (hash != null) {
            return hash;
        }
        MessageDigest digest = Crypto.sha256();
        digest.update((byte) (isText ? 1 : 0));
        digest.update(message);
        return digest.digest();
    }

    @Override
    final void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
        if (!hasPrunableData() && shouldLoadPrunable(transaction, includeExpiredPrunable)) {
            PrunableMessage prunableMessage = PrunableMessage.getPrunableMessage(transaction.getTransactionId());
            if (prunableMessage != null && prunableMessage.getMessage() != null) {
                this.prunableMessage = prunableMessage;
            }
        }
    }

    @Override
    boolean isPhasable() {
        return false;
    }

    @Override
    public final boolean hasPrunableData() {
        return (prunableMessage != null || message != null);
    }

    @Override
    public void restorePrunableData(Transaction transaction, int blockTimestamp, int height) {
        PrunableMessage.add((TransactionImpl) transaction, this, blockTimestamp, height);
    }
}
