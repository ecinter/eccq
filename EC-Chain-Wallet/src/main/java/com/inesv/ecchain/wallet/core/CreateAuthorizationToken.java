package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.kernel.core.Token;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static com.inesv.ecchain.wallet.core.JSONResponse.EC_INVALID_SECRET_PHRASE;


public final class CreateAuthorizationToken extends UserRequestHandler {

    static final CreateAuthorizationToken instance = new CreateAuthorizationToken();

    private CreateAuthorizationToken() {
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws IOException {
        String secretPhrase = req.getParameter("secretPhrase");
        if (!user.getSecretECPhrase().equals(secretPhrase)) {
            return EC_INVALID_SECRET_PHRASE;
        }

        String tokenString = Token.generateToken(secretPhrase, req.getParameter("website").trim());

        JSONObject response = new JSONObject();
        response.put("response", "showAuthorizationToken");
        response.put("token", tokenString);

        return response;
    }

    @Override
    boolean requirePost() {
        return true;
    }

}
