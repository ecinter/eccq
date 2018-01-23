package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import com.inesv.ecchain.kernel.core.PrunableMessage;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

import static com.inesv.ecchain.kernel.http.JSONResponses.PRUNED_TRANSACTION;


public final class DownloadPrunableMessage extends APIRequestHandler {

    static final DownloadPrunableMessage instance = new DownloadPrunableMessage();

    private DownloadPrunableMessage() {
        super(new APITag[]{APITag.MESSAGES}, "transaction", "secretPhrase", "sharedKey", "retrieve", "save");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request, HttpServletResponse response) throws EcException {
        long transactionId = ParameterParser.getUnsignedLong(request, "transaction", true);
        boolean retrieve = "true".equalsIgnoreCase(request.getParameter("retrieve"));
        PrunableMessage prunableMessage = PrunableMessage.getPrunableMessage(transactionId);
        if (prunableMessage == null && retrieve) {
            if (EcBlockchainProcessorImpl.getInstance().restorePrunedTransaction(transactionId) == null) {
                return PRUNED_TRANSACTION;
            }
            prunableMessage = PrunableMessage.getPrunableMessage(transactionId);
        }
        String secretPhrase = ParameterParser.getSecretPhrase(request, false);
        byte[] sharedKey = ParameterParser.getBytes(request, "sharedKey", false);
        if (sharedKey.length != 0 && secretPhrase != null) {
            return JSONResponses.either("secretPhrase", "sharedKey");
        }
        byte[] data = null;
        if (prunableMessage != null) {
            try {
                if (secretPhrase != null) {
                    data = prunableMessage.decrypt(secretPhrase);
                } else if (sharedKey.length > 0) {
                    data = prunableMessage.decrypt(sharedKey);
                } else {
                    data = prunableMessage.getMessage();
                }
            } catch (RuntimeException e) {
                LoggerUtil.logDebug("Decryption of message to recipient failed: " + e.toString());
                return JSONResponses.error("Wrong secretPhrase or sharedKey");
            }
        }
        if (data == null) {
            data = Convert.EC_EMPTY_BYTE;
        }
        String contentDisposition = "true".equalsIgnoreCase(request.getParameter("save")) ? "attachment" : "inline";
        response.setHeader("Content-Disposition", contentDisposition + "; filename=" + Long.toUnsignedString(transactionId));
        response.setContentLength(data.length);
        try (OutputStream out = response.getOutputStream()) {
            try {
                out.write(data);
            } catch (IOException e) {
                throw new ParameterException(JSONResponses.RESPONSE_WRITE_ERROR);
            }
        } catch (IOException e) {
            throw new ParameterException(JSONResponses.RESPONSE_STREAM_ERROR);
        }
        return null;
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws EcException {
        throw new UnsupportedOperationException();
    }
}
