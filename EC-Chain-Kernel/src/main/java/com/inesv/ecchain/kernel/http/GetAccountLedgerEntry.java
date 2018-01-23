package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.AccountLedger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public class GetAccountLedgerEntry extends APIRequestHandler {

    static final GetAccountLedgerEntry instance = new GetAccountLedgerEntry();

    private GetAccountLedgerEntry() {
        super(new APITag[]{APITag.ACCOUNTS}, "ledgerId", "includeTransaction", "includeHoldingInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        //
        // Process the request parameters
        //
        long ledgerId = ParameterParser.getUnsignedLong(req, "ledgerId", true);
        boolean includeTransaction = "true".equalsIgnoreCase(req.getParameter("includeTransaction"));
        boolean includeHoldingInfo = "true".equalsIgnoreCase(req.getParameter("includeHoldingInfo"));

        //
        // Get the ledger entry
        //
        AccountLedger.LedgerEntry ledgerEntry = AccountLedger.getEntry(ledgerId);
        if (ledgerEntry == null)
            return JSONResponses.UNKNOWN_ENTRY;
        //
        // Return the response
        //
        JSONObject response = new JSONObject();
        JSONData.ledgerEntry(response, ledgerEntry, includeTransaction, includeHoldingInfo);
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }
}
