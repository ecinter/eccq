package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class CoinReserveClaim extends CreateTransaction {

    static final CoinReserveClaim instance = new CoinReserveClaim();

    private CoinReserveClaim() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION}, "currency", "units");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        long units = ParameterParser.getLong(req, "units", 0, coin.getReserveSupply(), false);
        Account account = ParameterParser.getSenderAccount(req);
        Mortgaged mortgaged = new Mortgaged.MonetarySystemReserveClaim(coin.getId(), units);
        return createTransaction(req, account, mortgaged);

    }

}
