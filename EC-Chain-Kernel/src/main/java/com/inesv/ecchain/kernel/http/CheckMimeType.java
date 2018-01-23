package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.Search;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_TAGGED_DATA_FILE;


public final class CheckMimeType extends APIRequestHandler {

    static final CheckMimeType instance = new CheckMimeType();

    private CheckMimeType() {
        super("file", new APITag[]{APITag.DATA, APITag.UTILS}, "data", "filename", "isText");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        String filename = Convert.nullToEmpty(req.getParameter("filename")).trim();
        String dataValue = Convert.emptyToNull(req.getParameter("data"));
        byte[] data;
        if (dataValue == null) {
            try {
                Part part = req.getPart("file");
                if (part == null) {
                    throw new ParameterException(INCORRECT_TAGGED_DATA_FILE);
                }
                ParameterParser.FileData fileData = new ParameterParser.FileData(part).invoke();
                data = fileData.getData();
                // Depending on how the client submits the form, the filename, can be a regular parameter
                // or encoded in the multipart form. If its not a parameter we take from the form
                if (filename.isEmpty() && fileData.getFilename() != null) {
                    filename = fileData.getFilename();
                }
            } catch (IOException | ServletException e) {
                LoggerUtil.logError("error in reading file data", e);
                throw new ParameterException(INCORRECT_TAGGED_DATA_FILE);
            }
        } else {
            boolean isText = !"false".equalsIgnoreCase(req.getParameter("isText"));
            data = isText ? Convert.toBytes(dataValue) : Convert.parseHexString(dataValue);
        }

        JSONObject response = new JSONObject();
        response.put("type", Search.checkMimeType(data, filename));
        return response;
    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
