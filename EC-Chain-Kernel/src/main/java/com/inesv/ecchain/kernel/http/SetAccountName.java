package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.AccountName;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class SetAccountName extends CreateTransaction {

    static final SetAccountName instance = new SetAccountName();

    private SetAccountName() {
        super(new APITag[]{APITag.ALIASES, APITag.CREATE_TRANSACTION}, "aliasName", "aliasURI");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        String aliasName = Convert.emptyToNull(req.getParameter("aliasName"));
        String aliasURI = Convert.nullToEmpty(req.getParameter("aliasURI"));

        if (aliasName == null) {
            return MISSING_ALIAS_NAME;
        }

        aliasName = aliasName.trim();
        if (aliasName.length() == 0 || aliasName.length() > Constants.EC_MAX_ALIAS_LENGTH) {
            return INCORRECT_ALIAS_LENGTH;
        }

        String normalizedAlias = aliasName.toLowerCase();
        for (int i = 0; i < normalizedAlias.length(); i++) {
            if (Constants.EC_ALPHABET.indexOf(normalizedAlias.charAt(i)) < 0) {
                return INCORRECT_ALIAS_NAME;
            }
        }

        aliasURI = aliasURI.trim();
        if (aliasURI.length() > Constants.EC_MAX_ALIAS_URI_LENGTH) {
            return INCORRECT_URI_LENGTH;
        }

        Account account = ParameterParser.getSenderAccount(req);

        AccountName accountName = AccountName.getAlias(normalizedAlias);
        if (accountName != null && accountName.getAccountId() != account.getId()) {
            JSONObject response = new JSONObject();
            response.put("errorCode", 8);
            response.put("errorDescription", "\"" + aliasName + "\" is already used");
            return response;
        }

        Mortgaged mortgaged = new Mortgaged.MessagingAliasAssignment(aliasName, aliasURI);
        return createTransaction(req, account, mortgaged);

    }

}
