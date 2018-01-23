package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.AccountName;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ALIAS_NOTFORSALE;


public final class BuyAccountName extends CreateTransaction {

    static final BuyAccountName instance = new BuyAccountName();

    private BuyAccountName() {
        super(new APITag[]{APITag.ALIASES, APITag.CREATE_TRANSACTION}, "alias", "aliasName", "amountNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Account buyer = ParameterParser.getSenderAccount(req);
        AccountName accountName = ParameterParser.getAlias(req);
        long amountNQT = ParameterParser.getAmountNQT(req);
        if (AccountName.getOffer(accountName) == null) {
            return INCORRECT_ALIAS_NOTFORSALE;
        }
        long sellerId = accountName.getAccountId();
        Mortgaged mortgaged = new Mortgaged.MessagingAliasBuy(accountName.getPropertysName());
        return createTransaction(req, buyer, sellerId, amountNQT, mortgaged);
    }
}
