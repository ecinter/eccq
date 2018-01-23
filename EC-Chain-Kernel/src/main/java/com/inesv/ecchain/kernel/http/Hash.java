package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.crypto.HashFunction;
import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class Hash extends APIRequestHandler {

    static final Hash instance = new Hash();

    private Hash() {
        super(new APITag[]{APITag.UTILS}, "hashAlgorithm", "secret", "secretIsText");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        byte algorithm = ParameterParser.getByte(req, "hashAlgorithm", (byte) 0, Byte.MAX_VALUE, false);
        HashFunction hashFunction = null;
        try {
            hashFunction = HashFunction.getHashFunction(algorithm);
        } catch (IllegalArgumentException ignore) {
        }
        if (hashFunction == null) {
            return JSONResponses.INCORRECT_HASH_ALGORITHM;
        }

        boolean secretIsText = "true".equalsIgnoreCase(req.getParameter("secretIsText"));
        byte[] secret;
        try {
            secret = secretIsText ? Convert.toBytes(req.getParameter("secret"))
                    : Convert.parseHexString(req.getParameter("secret"));
        } catch (RuntimeException e) {
            return JSONResponses.INCORRECT_SECRET;
        }
        if (secret == null || secret.length == 0) {
            return JSONResponses.MISSING_SECRET;
        }

        JSONObject response = new JSONObject();
        response.put("hash", Convert.toHexString(hashFunction.hash(secret)));
        return response;
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
