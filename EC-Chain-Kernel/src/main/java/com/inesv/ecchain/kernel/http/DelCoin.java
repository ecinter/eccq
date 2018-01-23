package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Coin;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class DelCoin extends CreateTransaction {

    static final DelCoin instance = new DelCoin();

    private DelCoin() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION}, "currency");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        Account account = ParameterParser.getSenderAccount(req);
        if (!coin.canBeDeletedBy(account.getId())) {
            return JSONResponses.CANNOT_DELETE_CURRENCY;
        }
        Mortgaged mortgaged = new Mortgaged.MonetarySystemCurrencyDeletion(coin.getId());
        return createTransaction(req, account, mortgaged);
    }
}
