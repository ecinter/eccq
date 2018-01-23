package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.AccountLedger;
import com.inesv.ecchain.kernel.core.LedgerEvent;
import com.inesv.ecchain.kernel.core.LedgerHolding;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


public class GetAccountLedger extends APIRequestHandler {

    static final GetAccountLedger instance = new GetAccountLedger();

    private GetAccountLedger() {
        super(new APITag[]{APITag.ACCOUNTS}, "account", "firstIndex", "lastIndex",
                "eventType", "event", "holdingType", "holding", "includeTransactions", "includeHoldingInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        //
        // Process the request parameters
        //
        long accountId = ParameterParser.getAccountId(req, "account", false);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        String eventType = Convert.emptyToNull(req.getParameter("eventType"));
        LedgerEvent event = null;
        long eventId = 0;
        if (eventType != null) {
            try {
                event = LedgerEvent.valueOf(eventType);
                eventId = ParameterParser.getUnsignedLong(req, "event", false);
            } catch (RuntimeException e) {
                throw new ParameterException(JSONResponses.incorrect("eventType"));
            }
        }
        String holdingType = Convert.emptyToNull(req.getParameter("holdingType"));
        LedgerHolding holding = null;
        long holdingId = 0;
        if (holdingType != null) {
            try {
                holding = LedgerHolding.valueOf(holdingType);
                holdingId = ParameterParser.getUnsignedLong(req, "holding", false);
            } catch (RuntimeException e) {
                throw new ParameterException(JSONResponses.incorrect("holdingType"));
            }
        }
        boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));
        boolean includeHoldingInfo = "true".equalsIgnoreCase(req.getParameter("includeHoldingInfo"));

        //
        // Get the ledger entries
        //
        List<AccountLedger.LedgerEntry> ledgerEntries = AccountLedger.getEntries(accountId, event, eventId,
                holding, holdingId, firstIndex, lastIndex);
        //
        // Return the response
        //
        JSONArray responseEntries = new JSONArray();
        ledgerEntries.forEach((entry) -> {
            JSONObject responseEntry = new JSONObject();
            JSONData.ledgerEntry(responseEntry, entry, includeTransactions, includeHoldingInfo);
            responseEntries.add(responseEntry);
        });
        JSONObject response = new JSONObject();
        response.put("entries", responseEntries);
        return response;
    }
}
