package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class CoinBuy extends CreateTransaction {

    static final CoinBuy instance = new CoinBuy();

    private CoinBuy() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION}, "currency", "rateNQT", "units");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        long rateNQT = ParameterParser.getLong(req, "rateNQT", 0, Long.MAX_VALUE, true);
        long units = ParameterParser.getLong(req, "units", 0, Long.MAX_VALUE, true);
        Account account = ParameterParser.getSenderAccount(req);

        Mortgaged mortgaged = new Mortgaged.MonetarySystemExchangeBuy(coin.getId(), rateNQT, units);
        try {
            return createTransaction(req, account, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return JSONResponses.NOT_ENOUGH_FUNDS;
        }
    }

}
