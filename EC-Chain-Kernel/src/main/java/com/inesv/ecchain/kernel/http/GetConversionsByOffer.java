package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Conversion;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_OFFER;
import static com.inesv.ecchain.kernel.http.JSONResponses.MISSING_OFFER;

public final class GetConversionsByOffer extends APIRequestHandler {

    static final GetConversionsByOffer instance = new GetConversionsByOffer();

    private GetConversionsByOffer() {
        super(new APITag[]{APITag.MS}, "offer", "includeCurrencyInfo", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        // can't use ParameterParser.getCurrencyBuyOffer because offer may have been already deleted
        String offerValue = Convert.emptyToNull(req.getParameter("offer"));
        if (offerValue == null) {
            throw new ParameterException(MISSING_OFFER);
        }
        long offerId;
        try {
            offerId = Convert.parseUnsignedLong(offerValue);
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_OFFER);
        }
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        JSONObject response = new JSONObject();
        JSONArray exchangesData = new JSONArray();
        try (H2Iterator<Conversion> exchanges = Conversion.getOfferConvert(offerId, firstIndex, lastIndex)) {
            while (exchanges.hasNext()) {
                exchangesData.add(JSONData.exchange(exchanges.next(), includeCurrencyInfo));
            }
        }
        response.put("exchanges", exchangesData);
        return response;
    }

}
