package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.BadgeData;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static com.inesv.ecchain.kernel.http.JSONResponses.PRUNED_TRANSACTION;


public final class DownloadTaggedData extends APIRequestHandler {

    static final DownloadTaggedData instance = new DownloadTaggedData();

    private DownloadTaggedData() {
        super(new APITag[]{APITag.DATA}, "transaction", "retrieve");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request, HttpServletResponse response) throws EcException {
        long transactionId = ParameterParser.getUnsignedLong(request, "transaction", true);
        boolean retrieve = "true".equalsIgnoreCase(request.getParameter("retrieve"));
        BadgeData badgeData = BadgeData.getData(transactionId);
        if (badgeData == null && retrieve) {
            if (EcBlockchainProcessorImpl.getInstance().restorePrunedTransaction(transactionId) == null) {
                return PRUNED_TRANSACTION;
            }
            badgeData = BadgeData.getData(transactionId);
        }
        if (badgeData == null) {
            return JSONResponses.incorrect("transaction", "Tagged data not found");
        }
        byte[] data = badgeData.getData();
        if (!badgeData.getType().equals("")) {
            response.setContentType(badgeData.getType());
        } else {
            response.setContentType("application/octet-stream");
        }
        String filename = badgeData.getFilename();
        if (filename == null || filename.trim().isEmpty()) {
            filename = badgeData.getName().trim();
        }
        String contentDisposition = "attachment";
        try {
            URI uri = new URI(null, null, filename, null);
            contentDisposition += "; filename*=UTF-8''" + uri.toASCIIString();
        } catch (URISyntaxException ignore) {
        }
        response.setHeader("Content-Disposition", contentDisposition);
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
