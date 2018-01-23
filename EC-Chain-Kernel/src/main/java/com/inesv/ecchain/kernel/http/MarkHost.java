package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.peer.QualityProof;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class MarkHost extends APIRequestHandler {

    static final MarkHost instance = new MarkHost();

    private MarkHost() {
        super(new APITag[]{APITag.TOKENS}, "secretPhrase", "host", "weight", "date");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        String host = Convert.emptyToNull(req.getParameter("host"));
        String weightValue = Convert.emptyToNull(req.getParameter("weight"));
        String dateValue = Convert.emptyToNull(req.getParameter("date"));
        if (host == null) {
            return MISSING_HOST;
        } else if (weightValue == null) {
            return MISSING_WEIGHT;
        } else if (dateValue == null) {
            return MISSING_DATE;
        }

        if (host.length() > 100) {
            return INCORRECT_HOST;
        }

        int weight;
        try {
            weight = Integer.parseInt(weightValue);
            if (weight <= 0 || weight > Constants.MAX_BALANCE_EC) {
                return INCORRECT_WEIGHT;
            }
        } catch (NumberFormatException e) {
            return INCORRECT_WEIGHT;
        }

        try {

            String hallmark = QualityProof.generateHallmark(secretPhrase, host, weight, QualityProof.parseDate(dateValue));

            JSONObject response = new JSONObject();
            response.put("hallmark", hallmark);
            return response;

        } catch (RuntimeException e) {
            return INCORRECT_DATE;
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
