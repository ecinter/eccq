package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.AccountName;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ALIAS_OWNER;


public final class DelAccountName extends CreateTransaction {

    static final DelAccountName instance = new DelAccountName();

    private DelAccountName() {
        super(new APITag[]{APITag.ALIASES, APITag.CREATE_TRANSACTION}, "alias", "aliasName");
    }

    @Override
    protected JSONStreamAware processRequest(final HttpServletRequest req) throws EcException {
        final AccountName accountName = ParameterParser.getAlias(req);
        final Account owner = ParameterParser.getSenderAccount(req);

        if (accountName.getAccountId() != owner.getId()) {
            return INCORRECT_ALIAS_OWNER;
        }

        final Mortgaged mortgaged = new Mortgaged.MessagingAliasDelete(accountName.getPropertysName());
        return createTransaction(req, owner, mortgaged);
    }
}
