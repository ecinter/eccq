package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Coin;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;



public final class CoinReserveIncrease extends CreateTransaction {

    static final CoinReserveIncrease instance = new CoinReserveIncrease();

    private CoinReserveIncrease() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION}, "currency", "amountPerUnitNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        long amountPerUnitNQT = ParameterParser.getLong(req, "amountPerUnitNQT", 1L, Constants.EC_MAX_BALANCE_NQT, true);
        Account account = ParameterParser.getSenderAccount(req);
        Mortgaged mortgaged = new Mortgaged.MonetarySystemReserveIncrease(coin.getId(), amountPerUnitNQT);
        return createTransaction(req, account, mortgaged);

    }

}
