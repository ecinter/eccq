package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ACCOUNT_PROPERTY_NAME_LENGTH;
import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ACCOUNT_PROPERTY_VALUE_LENGTH;


public final class SetAccountProperty extends CreateTransaction {

    static final SetAccountProperty instance = new SetAccountProperty();

    private SetAccountProperty() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.CREATE_TRANSACTION}, "recipient", "property", "value");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Account senderAccount = ParameterParser.getSenderAccount(req);
        long recipientId = ParameterParser.getAccountId(req, "recipient", false);
        if (recipientId == 0) {
            recipientId = senderAccount.getId();
        }
        String property = Convert.nullToEmpty(req.getParameter("property")).trim();
        String value = Convert.nullToEmpty(req.getParameter("value")).trim();

        if (property.length() > Constants.EC_MAX_ACCOUNT_PROPERTY_NAME_LENGTH || property.length() == 0) {
            return INCORRECT_ACCOUNT_PROPERTY_NAME_LENGTH;
        }

        if (value.length() > Constants.EC_MAX_ACCOUNT_PROPERTY_VALUE_LENGTH) {
            return INCORRECT_ACCOUNT_PROPERTY_VALUE_LENGTH;
        }

        Mortgaged mortgaged = new Mortgaged.MessagingAccountProperty(property, value);
        return createTransaction(req, senderAccount, recipientId, 0, mortgaged);

    }

}
