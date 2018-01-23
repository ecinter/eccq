package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.Token;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class DecipherToken extends APIRequestHandler {

    static final DecipherToken instance = new DecipherToken();

    private DecipherToken() {
        super(new APITag[]{APITag.TOKENS}, "website", "token");
    }

    @Override
    public JSONStreamAware processRequest(HttpServletRequest req) {

        String website = req.getParameter("website");
        String tokenString = req.getParameter("token");
        if (website == null) {
            return MISSING_WEBSITE;
        } else if (tokenString == null) {
            return MISSING_TOKEN;
        }

        try {

            Token token = Token.parseToken(tokenString, website.trim());

            return JSONData.token(token);

        } catch (RuntimeException e) {
            return INCORRECT_WEBSITE;
        }
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
