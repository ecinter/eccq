package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.CoinBuyOffer;
import com.inesv.ecchain.kernel.core.CoinSellOffer;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetOffer extends APIRequestHandler {

    static final GetOffer instance = new GetOffer();

    private GetOffer() {
        super(new APITag[]{APITag.MS}, "offer");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        JSONObject response = new JSONObject();
        CoinBuyOffer buyOffer = ParameterParser.getBuyOffer(req);
        CoinSellOffer sellOffer = ParameterParser.getSellOffer(req);
        response.put("buyOffer", JSONData.offer(buyOffer));
        response.put("sellOffer", JSONData.offer(sellOffer));
        return response;
    }

}
