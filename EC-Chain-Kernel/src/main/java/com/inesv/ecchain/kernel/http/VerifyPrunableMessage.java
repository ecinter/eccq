package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.Enclosure;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.PrunablePlainMessage;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class VerifyPrunableMessage extends APIRequestHandler {

    static final VerifyPrunableMessage instance = new VerifyPrunableMessage();

    private static final JSONStreamAware EC_NO_SUCH_PLAIN_MESSAGE;
    private static final JSONStreamAware EC_NO_SUCH_ENCRYPTED_MESSAGE;

    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "This transaction has no plain message attachment");
        EC_NO_SUCH_PLAIN_MESSAGE = JSON.prepare(response);
        response.clear();
        response.put("errorCode", 5);
        response.put("errorDescription", "This transaction has no encrypted message attachment");
        EC_NO_SUCH_ENCRYPTED_MESSAGE = JSON.prepare(response);
    }


    private VerifyPrunableMessage() {
        super(new APITag[]{APITag.MESSAGES}, "transaction",
                "message", "messageIsText",
                "messageToEncryptIsText", "encryptedMessageData", "encryptedMessageNonce", "compressMessageToEncrypt");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        Transaction transaction = EcBlockchainImpl.getInstance().getTransaction(transactionId);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }

        PrunablePlainMessage plainMessage = (PrunablePlainMessage) ParameterParser.getPlainMessage(req, true);
        Enclosure.PrunableEncryptedMessage encryptedMessage = (Enclosure.PrunableEncryptedMessage) ParameterParser.getEncryptedMessage(req, null, true);

        if (plainMessage == null && encryptedMessage == null) {
            return MISSING_MESSAGE_ENCRYPTED_MESSAGE;
        }
        if (plainMessage != null && encryptedMessage != null) {
            return EITHER_MESSAGE_ENCRYPTED_MESSAGE;
        }

        if (plainMessage != null) {
            PrunablePlainMessage myPlainMessage = transaction.getPrunablePlainMessage();
            if (myPlainMessage == null) {
                return EC_NO_SUCH_PLAIN_MESSAGE;
            }
            if (!Arrays.equals(myPlainMessage.getHash(), plainMessage.getHash())) {
                return JSONResponses.HASHES_MISMATCH;
            }
            JSONObject response = myPlainMessage.getJSONObject();
            response.put("ecVerify", true);
            return response;
        } else if (encryptedMessage != null) {
            Enclosure.PrunableEncryptedMessage myEncryptedMessage = transaction.getPrunableEncryptedMessage();
            if (myEncryptedMessage == null) {
                return EC_NO_SUCH_ENCRYPTED_MESSAGE;
            }
            if (!Arrays.equals(myEncryptedMessage.getHash(), encryptedMessage.getHash())) {
                return JSONResponses.HASHES_MISMATCH;
            }
            JSONObject response = myEncryptedMessage.getJSONObject();
            response.put("ecVerify", true);
            return response;
        }

        return JSON.EMPTY_JSON;
    }

}
