package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Coin;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.NOT_ENOUGH_CURRENCY;


public final class TransferCurrency extends CreateTransaction {

    static final TransferCurrency instance = new TransferCurrency();

    private TransferCurrency() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION}, "recipient", "currency", "units");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long recipient = ParameterParser.getAccountId(req, "recipient", true);

        Coin coin = ParameterParser.getCurrency(req);
        long units = ParameterParser.getLong(req, "units", 0, Long.MAX_VALUE, true);
        Account account = ParameterParser.getSenderAccount(req);

        Mortgaged mortgaged = new Mortgaged.MonetarySystemCurrencyTransfer(coin.getId(), units);
        try {
            return createTransaction(req, account, recipient, 0, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return NOT_ENOUGH_CURRENCY;
        }
    }

}
