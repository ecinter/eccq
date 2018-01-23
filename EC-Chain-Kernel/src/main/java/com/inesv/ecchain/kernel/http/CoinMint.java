package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class CoinMint extends CreateTransaction {

    static final CoinMint instance = new CoinMint();

    private CoinMint() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION}, "currency", "nonce", "units", "counter");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        long nonce = ParameterParser.getLong(req, "nonce", Long.MIN_VALUE, Long.MAX_VALUE, true);
        long units = ParameterParser.getLong(req, "units", 0, Long.MAX_VALUE, true);
        long counter = ParameterParser.getLong(req, "counter", 0, Integer.MAX_VALUE, true);
        Account account = ParameterParser.getSenderAccount(req);

        Mortgaged mortgaged = new Mortgaged.MonetarySystemCurrencyMinting(nonce, coin.getId(), units, counter);
        return createTransaction(req, account, mortgaged);
    }

}
