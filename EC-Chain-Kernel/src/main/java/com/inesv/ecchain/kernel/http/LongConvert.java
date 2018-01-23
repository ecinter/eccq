package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;

public final class LongConvert extends APIRequestHandler {

    static final LongConvert instance = new LongConvert();

    private LongConvert() {
        super(new APITag[]{APITag.UTILS}, "id");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        String id = Convert.emptyToNull(req.getParameter("id"));
        if (id == null) {
            return JSON.EMPTY_JSON;
        }
        JSONObject response = new JSONObject();
        BigInteger bigInteger = new BigInteger(id);
        if (bigInteger.signum() < 0) {
            if (bigInteger.negate().compareTo(Convert.TWO_64) > 0) {
                return JSONResponses.OVERFLOW;
            } else {
                response.put("stringId", bigInteger.add(Convert.TWO_64).toString());
                response.put("longId", String.valueOf(bigInteger.longValue()));
            }
        } else {
            if (bigInteger.compareTo(Convert.TWO_64) >= 0) {
                return JSONResponses.OVERFLOW;
            } else {
                response.put("stringId", bigInteger.toString());
                response.put("longId", String.valueOf(bigInteger.longValue()));
            }
        }
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
