package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.peer.QualityProof;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_HALLMARK;
import static com.inesv.ecchain.kernel.http.JSONResponses.MISSING_HALLMARK;


public final class DecipherHallmark extends APIRequestHandler {

    static final DecipherHallmark instance = new DecipherHallmark();

    private DecipherHallmark() {
        super(new APITag[]{APITag.TOKENS}, "hallmark");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        String hallmarkValue = req.getParameter("hallmark");
        if (hallmarkValue == null) {
            return MISSING_HALLMARK;
        }

        try {

            QualityProof qualityProof = QualityProof.parseHallmark(hallmarkValue);

            return JSONData.hallmark(qualityProof);

        } catch (RuntimeException e) {
            return INCORRECT_HALLMARK;
        }
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
