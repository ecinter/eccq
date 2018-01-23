package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class DelAccountProperty extends CreateTransaction {

    static final DelAccountProperty instance = new DelAccountProperty();

    private DelAccountProperty() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.CREATE_TRANSACTION}, "recipient", "property", "setter");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Account senderAccount = ParameterParser.getSenderAccount(req);
        long recipientId = ParameterParser.getAccountId(req, "recipient", false);
        if (recipientId == 0) {
            recipientId = senderAccount.getId();
        }
        long setterId = ParameterParser.getAccountId(req, "setter", false);
        if (setterId == 0) {
            setterId = senderAccount.getId();
        }
        String property = Convert.nullToEmpty(req.getParameter("property")).trim();
        if (property.isEmpty()) {
            return JSONResponses.MISSING_PROPERTY;
        }
        Account.AccountProperty accountProperty = Account.getProperty(recipientId, property, setterId);
        if (accountProperty == null) {
            return JSONResponses.UNKNOWN_PROPERTY;
        }
        if (accountProperty.getRecipientId() != senderAccount.getId() && accountProperty.getSetterId() != senderAccount.getId()) {
            return JSONResponses.INCORRECT_PROPERTY;
        }
        Mortgaged mortgaged = new Mortgaged.MessagingAccountPropertyDelete(accountProperty.getId());
        return createTransaction(req, senderAccount, recipientId, 0, mortgaged);

    }

}
