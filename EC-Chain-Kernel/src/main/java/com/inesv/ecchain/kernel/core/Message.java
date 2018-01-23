package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @Author:Lin
 * @Description:
 * @Date:20:13 2017/12/21
 * @Modified by:
 */
public class Message extends AbstractEnclosure {

    private static final String appendixName = "Message";
    private static final Fee MESSAGE_FEE = new Fee.SizeBasedFee(0, Constants.ONE_EC, 32) {
        @Override
        public int getSize(TransactionImpl transaction, Enclosure appendage) {
            return ((com.inesv.ecchain.kernel.core.Message) appendage).getMessage().length;
        }
    };
    private final byte[] message;
    private final boolean isText;

    Message(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
        super(buffer, transactionVersion);
        int messageLength = buffer.getInt();
        this.isText = messageLength < 0; // ugly hack
        if (messageLength < 0) {
            messageLength &= Integer.MAX_VALUE;
        }
        if (messageLength > 1000) {
            throw new EcNotValidExceptionEc("Invalid arbitrary message length: " + messageLength);
        }
        this.message = new byte[messageLength];
        buffer.get(this.message);
        if (isText && !Arrays.equals(message, Convert.toBytes(Convert.toString(message)))) {
            throw new EcNotValidExceptionEc("Message is not UTF-8 text");
        }
    }

    Message(JSONObject attachmentData) {
        super(attachmentData);
        String messageString = (String) attachmentData.get("message");
        this.isText = Boolean.TRUE.equals(attachmentData.get("messageIsText"));
        this.message = isText ? Convert.toBytes(messageString) : Convert.parseHexString(messageString);
    }

    public Message(byte[] message) {
        this(message, false);
    }

    public Message(String string) {
        this(Convert.toBytes(string), true);
    }

    public Message(String string, boolean isText) {
        this(isText ? Convert.toBytes(string) : Convert.parseHexString(string), isText);
    }

    public Message(byte[] message, boolean isText) {
        this.message = message;
        this.isText = isText;
    }

    static com.inesv.ecchain.kernel.core.Message parse(JSONObject attachmentData) {
        if (!Enclosure.hasEnclosure(appendixName, attachmentData)) {
            return null;
        }
        return new com.inesv.ecchain.kernel.core.Message(attachmentData);
    }

    @Override
    String getAppendixName() {
        return appendixName;
    }

    @Override
    int getMySize() {
        return 4 + message.length;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
        buffer.putInt(isText ? (message.length | Integer.MIN_VALUE) : message.length);
        buffer.put(message);
    }

    @Override
    void putMyJSON(JSONObject json) {
        json.put("message", Convert.toString(message, isText));
        json.put("messageIsText", isText);
    }

    @Override
    public Fee getBaselineFee(Transaction transaction) {
        return MESSAGE_FEE;
    }

    @Override
    void validate(Transaction transaction) throws EcValidationException {
        if (EcBlockchainImpl.getInstance().getHeight() > Constants.EC_SHUFFLING_BLOCK && message.length > Constants.EC_MAX_ARBITRARY_MESSAGE_LENGTH) {
            throw new EcNotValidExceptionEc("Invalid arbitrary message length: " + message.length);
        }
    }

    @Override
    void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
    }

    public byte[] getMessage() {
        return message;
    }

    public boolean isText() {
        return isText;
    }

    @Override
    boolean isPhasable() {
        return false;
    }

}
