package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class PublishExchangeOffer extends CreateTransaction {

    static final PublishExchangeOffer instance = new PublishExchangeOffer();

    private PublishExchangeOffer() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION}, "currency", "buyRateNQT", "sellRateNQT",
                "totalBuyLimit", "totalSellLimit", "initialBuySupply", "initialSellSupply", "expirationHeight");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        long buyRateNQT = ParameterParser.getLong(req, "buyRateNQT", 0, Long.MAX_VALUE, true);
        long sellRateNQT = ParameterParser.getLong(req, "sellRateNQT", 0, Long.MAX_VALUE, true);
        long totalBuyLimit = ParameterParser.getLong(req, "totalBuyLimit", 0, Long.MAX_VALUE, true);
        long totalSellLimit = ParameterParser.getLong(req, "totalSellLimit", 0, Long.MAX_VALUE, true);
        long initialBuySupply = ParameterParser.getLong(req, "initialBuySupply", 0, Long.MAX_VALUE, true);
        long initialSellSupply = ParameterParser.getLong(req, "initialSellSupply", 0, Long.MAX_VALUE, true);
        int expirationHeight = ParameterParser.getInt(req, "expirationHeight", 0, Integer.MAX_VALUE, true);
        Account account = ParameterParser.getSenderAccount(req);

        Mortgaged mortgaged = new Mortgaged.MonetarySystemPublishExchangeOffer(coin.getId(), buyRateNQT, sellRateNQT,
                totalBuyLimit, totalSellLimit, initialBuySupply, initialSellSupply, expirationHeight);
        try {
            return createTransaction(req, account, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return JSONResponses.NOT_ENOUGH_FUNDS;
        }
    }

}
