package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.CoinExchangeOffer;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetCanToSell extends APIRequestHandler {

    static final GetCanToSell instance = new GetCanToSell();

    private GetCanToSell() {
        super(new APITag[]{APITag.MS}, "currency", "units");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", true);
        long units = ParameterParser.getLong(req, "units", 1L, Long.MAX_VALUE, true);
        CoinExchangeOffer.AvailableOffers availableOffers = CoinExchangeOffer.getAvailableToSell(currencyId, units);
        return JSONData.availableOffers(availableOffers);
    }

}
