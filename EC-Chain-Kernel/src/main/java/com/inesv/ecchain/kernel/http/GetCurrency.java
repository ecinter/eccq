package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.MISSING_CURRENCY;
import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_CURRENCY;


public final class GetCurrency extends APIRequestHandler {

    static final GetCurrency instance = new GetCurrency();

    private GetCurrency() {
        super(new APITag[]{APITag.MS}, "currency", "code", "includeCounts");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        boolean includeCounts = "true".equalsIgnoreCase(req.getParameter("includeCounts"));
        long currencyId = ParameterParser.getUnsignedLong(req, "coin", false);
        Coin coin;
        if (currencyId == 0) {
            String currencyCode = Convert.emptyToNull(req.getParameter("code"));
            if (currencyCode == null) {
                return MISSING_CURRENCY;
            }
            coin = Coin.getCoinByCode(currencyCode);
        } else {
            coin = Coin.getCoin(currencyId);
        }
        if (coin == null) {
            throw new ParameterException(UNKNOWN_CURRENCY);
        }
        return JSONData.currency(coin, includeCounts);
    }

}
