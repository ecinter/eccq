package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;



public final class StartFundingMonitor extends APIRequestHandler {

    static final StartFundingMonitor instance = new StartFundingMonitor();

    private StartFundingMonitor() {
        super(new APITag[]{APITag.ACCOUNTS}, "holdingType", "holding", "property", "amount", "threshold",
                "interval", "secretPhrase");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        HoldingType holdingType = ParameterParser.getHoldingType(req);
        long holdingId = ParameterParser.getHoldingId(req, holdingType);
        String property = ParameterParser.getAccountProperty(req, true);
        long amount = ParameterParser.getLong(req, "amount", 0, Long.MAX_VALUE, true);
        if (amount < Constants.EC_MIN_FUND_AMOUNT) {
            throw new ParameterException(incorrect("amount", "Minimum funding amount is " + Constants.EC_MIN_FUND_AMOUNT));
        }
        long threshold = ParameterParser.getLong(req, "threshold", 0, Long.MAX_VALUE, true);
        if (threshold < Constants.EC_MIN_FUND_THRESHOLD) {
            throw new ParameterException(incorrect("threshold", "Minimum funding threshold is " + Constants.EC_MIN_FUND_THRESHOLD));
        }
        int interval = ParameterParser.getInt(req, "interval", Constants.EC_MIN_FUND_INTERVAL, Integer.MAX_VALUE, true);
        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        switch (holdingType) {
            case ASSET:
                Property asset = Property.getAsset(holdingId);
                if (asset == null) {
                    throw new ParameterException(JSONResponses.UNKNOWN_ASSET);
                }
                break;
            case CURRENCY:
                Coin coin = Coin.getCoin(holdingId);
                if (coin == null) {
                    throw new ParameterException(JSONResponses.UNKNOWN_CURRENCY);
                }
                break;
        }
        Account account = Account.getAccount(Crypto.getPublicKey(secretPhrase));
        if (account == null) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        if (FundMonitoring.startMonitor(holdingType, holdingId, property, amount, threshold, interval, secretPhrase)) {
            JSONObject response = new JSONObject();
            response.put("started", true);
            return response;
        } else {
            return MONITOR_ALREADY_STARTED;
        }
    }

    @Override
    protected boolean requirePost() {
        return true;
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
