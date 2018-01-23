package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.AccountName;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ALIAS_OWNER;
import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_RECIPIENT;


public final class SellAccountName extends CreateTransaction {

    static final SellAccountName instance = new SellAccountName();

    private SellAccountName() {
        super(new APITag[]{APITag.ALIASES, APITag.CREATE_TRANSACTION}, "alias", "aliasName", "recipient", "priceNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        AccountName accountName = ParameterParser.getAlias(req);
        Account owner = ParameterParser.getSenderAccount(req);

        long priceNQT = ParameterParser.getLong(req, "priceNQT", 0L, Constants.EC_MAX_BALANCE_NQT, true);

        String recipientValue = Convert.emptyToNull(req.getParameter("recipient"));
        long recipientId = 0;
        if (recipientValue != null) {
            try {
                recipientId = Convert.parseAccountId(recipientValue);
            } catch (RuntimeException e) {
                return INCORRECT_RECIPIENT;
            }
            if (recipientId == 0) {
                return INCORRECT_RECIPIENT;
            }
        }

        if (accountName.getAccountId() != owner.getId()) {
            return INCORRECT_ALIAS_OWNER;
        }

        Mortgaged mortgaged = new Mortgaged.MessagingAliasSell(accountName.getPropertysName(), priceNQT);
        return createTransaction(req, owner, recipientId, 0, mortgaged);
    }
}
