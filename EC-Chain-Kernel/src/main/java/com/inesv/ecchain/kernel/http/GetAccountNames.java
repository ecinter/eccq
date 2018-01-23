package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.AccountName;
import com.inesv.ecchain.kernel.H2.FilteringIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountNames extends APIRequestHandler {

    static final GetAccountNames instance = new GetAccountNames();

    private GetAccountNames() {
        super(new APITag[]{APITag.ALIASES}, "timestamp", "account", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        final int timestamp = ParameterParser.getTimestamp(req);
        final long accountId = ParameterParser.getAccountId(req, true);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray aliases = new JSONArray();
        try (FilteringIterator<AccountName> aliasIterator = new FilteringIterator<>(AccountName.getAliasesByOwner(accountId, 0, -1),
                alias -> alias.getTimestamp() >= timestamp, firstIndex, lastIndex)) {
            while (aliasIterator.hasNext()) {
                aliases.add(JSONData.alias(aliasIterator.next()));
            }
        }

        JSONObject response = new JSONObject();
        response.put("aliases", aliases);
        return response;
    }

}
