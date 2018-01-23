package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Token;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_FILE;
import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_TOKEN;


public final class CreateFileToken extends APIRequestHandler {

    static final CreateFileToken instance = new CreateFileToken();

    private CreateFileToken() {
        super("file", new APITag[]{APITag.TOKENS}, "secretPhrase");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        byte[] data;
        try {
            Part part = req.getPart("file");
            if (part == null) {
                throw new ParameterException(INCORRECT_FILE);
            }
            ParameterParser.FileData fileData = new ParameterParser.FileData(part).invoke();
            data = fileData.getData();
        } catch (IOException | ServletException e) {
            throw new ParameterException(INCORRECT_FILE);
        }
        try {
            String tokenString = Token.generateToken(secretPhrase, data);
            JSONObject response = JSONData.token(Token.parseToken(tokenString, data));
            response.put("token", tokenString);
            return response;
        } catch (RuntimeException e) {
            return INCORRECT_TOKEN;
        }
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
