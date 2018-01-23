package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class ReadMessage extends APIRequestHandler {

    static final ReadMessage instance = new ReadMessage();

    private ReadMessage() {
        super(new APITag[]{APITag.MESSAGES}, "transaction", "secretPhrase", "sharedKey", "retrieve");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        boolean retrieve = "true".equalsIgnoreCase(req.getParameter("retrieve"));
        Transaction transaction = EcBlockchainImpl.getInstance().getTransaction(transactionId);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }
        PrunableMessage prunableMessage = PrunableMessage.getPrunableMessage(transactionId);
        if (prunableMessage == null && (transaction.getPrunablePlainMessage() != null || transaction.getPrunableEncryptedMessage() != null) && retrieve) {
            if (EcBlockchainProcessorImpl.getInstance().restorePrunedTransaction(transactionId) == null) {
                return PRUNED_TRANSACTION;
            }
            prunableMessage = PrunableMessage.getPrunableMessage(transactionId);
        }

        JSONObject response = new JSONObject();
        Message message = transaction.getMessage();
        Enclosure.EncryptedMessage encryptedMessage = transaction.getEncryptedMessage();
        Enclosure.EncryptToSelfMessage encryptToSelfMessage = transaction.getEncryptToSelfMessage();
        if (message == null && encryptedMessage == null && encryptToSelfMessage == null && prunableMessage == null) {
            return NO_MESSAGE;
        }
        if (message != null) {
            response.put("message", Convert.toString(message.getMessage(), message.isText()));
            response.put("messageIsPrunable", false);
        } else if (prunableMessage != null && prunableMessage.getMessage() != null) {
            response.put("message", Convert.toString(prunableMessage.getMessage(), prunableMessage.messageIsText()));
            response.put("messageIsPrunable", true);
        }
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        byte[] sharedKey = ParameterParser.getBytes(req, "sharedKey", false);
        if (sharedKey.length != 0 && secretPhrase != null) {
            return JSONResponses.either("secretPhrase", "sharedKey");
        }
        if (secretPhrase != null || sharedKey.length > 0) {
            EncryptedData encryptedData = null;
            boolean isText = false;
            boolean uncompress = true;
            if (encryptedMessage != null) {
                encryptedData = encryptedMessage.getEncryptedData();
                isText = encryptedMessage.isText();
                uncompress = encryptedMessage.isCompressed();
                response.put("encryptedMessageIsPrunable", false);
            } else if (prunableMessage != null && prunableMessage.getEncryptedData() != null) {
                encryptedData = prunableMessage.getEncryptedData();
                isText = prunableMessage.encryptedMessageIsText();
                uncompress = prunableMessage.isCompressed();
                response.put("encryptedMessageIsPrunable", true);
            }
            if (encryptedData != null) {
                try {
                    byte[] decrypted = null;
                    if (secretPhrase != null) {
                        byte[] readerPublicKey = Crypto.getPublicKey(secretPhrase);
                        byte[] senderPublicKey = Account.getPublicKey(transaction.getSenderId());
                        byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
                        byte[] publicKey = Arrays.equals(senderPublicKey, readerPublicKey) ? recipientPublicKey : senderPublicKey;
                        if (publicKey != null) {
                            decrypted = Account.decryptFrom(publicKey, encryptedData, secretPhrase, uncompress);
                        }
                    } else {
                        decrypted = Crypto.aesDecrypt(encryptedData.getData(), sharedKey);
                        if (uncompress) {
                            decrypted = Convert.uncompress(decrypted);
                        }
                    }
                    response.put("decryptedMessage", Convert.toString(decrypted, isText));
                } catch (RuntimeException e) {
                    LoggerUtil.logDebug("Decryption of message to recipient failed: " + e.toString());
                    JSONData.putException(response, e, "Wrong secretPhrase or sharedKey");
                }
            }
            if (encryptToSelfMessage != null && secretPhrase != null) {
                byte[] publicKey = Crypto.getPublicKey(secretPhrase);
                try {
                    byte[] decrypted = Account.decryptFrom(publicKey, encryptToSelfMessage.getEncryptedData(), secretPhrase, encryptToSelfMessage.isCompressed());
                    response.put("decryptedMessageToSelf", Convert.toString(decrypted, encryptToSelfMessage.isText()));
                } catch (RuntimeException e) {
                    LoggerUtil.logDebug("Decryption of message to self failed: " + e.toString());
                }
            }
        }
        return response;
    }

}
