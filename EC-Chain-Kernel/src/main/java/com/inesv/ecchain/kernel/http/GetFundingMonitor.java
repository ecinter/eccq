package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.FundMonitoring;
import com.inesv.ecchain.kernel.core.HoldingType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


public class GetFundingMonitor extends APIRequestHandler {

    static final GetFundingMonitor instance = new GetFundingMonitor();

    private GetFundingMonitor() {
        super(new APITag[]{APITag.ACCOUNTS}, "holdingType", "holding", "property", "secretPhrase",
                "includeMonitoredAccounts", "account", "adminPassword");
    }


    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        long account = ParameterParser.getAccountId(req, false);
        boolean includeMonitoredAccounts = "true".equalsIgnoreCase(req.getParameter("includeMonitoredAccounts"));
        if (secretPhrase == null) {
            API.verifyPassword(req);
        }
        List<FundMonitoring> monitors;
        if (secretPhrase != null || account != 0) {
            if (secretPhrase != null) {
                if (account != 0) {
                    if (Account.getId(Crypto.getPublicKey(secretPhrase)) != account) {
                        return JSONResponses.INCORRECT_ACCOUNT;
                    }
                } else {
                    account = Account.getId(Crypto.getPublicKey(secretPhrase));
                }
            }
            final long accountId = account;
            final HoldingType holdingType = ParameterParser.getHoldingType(req);
            final long holdingId = ParameterParser.getHoldingId(req, holdingType);
            final String property = ParameterParser.getAccountProperty(req, false);
            Filter<FundMonitoring> filter;
            if (property != null) {
                filter = (monitor) -> monitor.getAccountId() == accountId &&
                        monitor.getProperty().equals(property) &&
                        monitor.getHoldingType() == holdingType &&
                        monitor.getHoldingId() == holdingId;
            } else {
                filter = (monitor) -> monitor.getAccountId() == accountId;
            }
            monitors = FundMonitoring.getMonitors(filter);
        } else {
            monitors = FundMonitoring.getAllMonitors();
        }
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        monitors.forEach(monitor -> {
            JSONObject monitorJSON = JSONData.accountMonitor(monitor, includeMonitoredAccounts);
            jsonArray.add(monitorJSON);
        });
        response.put("monitors", jsonArray);
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireFullClient() {
        return true;
    }

}
