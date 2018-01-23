package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ACCOUNT_DESCRIPTION_LENGTH;
import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ACCOUNT_NAME_LENGTH;


public final class SetAccountInfo extends CreateTransaction {

    static final SetAccountInfo instance = new SetAccountInfo();

    private SetAccountInfo() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.CREATE_TRANSACTION}, "name", "description");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        String name = Convert.nullToEmpty(req.getParameter("name")).trim();
        String description = Convert.nullToEmpty(req.getParameter("description")).trim();

        if (name.length() > Constants.EC_MAX_ACCOUNT_NAME_LENGTH) {
            return INCORRECT_ACCOUNT_NAME_LENGTH;
        }

        if (description.length() > Constants.EC_MAX_ACCOUNT_DESCRIPTION_LENGTH) {
            return INCORRECT_ACCOUNT_DESCRIPTION_LENGTH;
        }

        Account account = ParameterParser.getSenderAccount(req);
        Mortgaged mortgaged = new Mortgaged.MessagingAccountInfo(name, description);
        return createTransaction(req, account, mortgaged);

    }

}
