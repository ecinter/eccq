package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class RequeueUnconfirmedTransactions extends APIRequestHandler {

    static final RequeueUnconfirmedTransactions instance = new RequeueUnconfirmedTransactions();

    private RequeueUnconfirmedTransactions() {
        super(new APITag[]{APITag.DEBUG});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        try {
            TransactionProcessorImpl.getInstance().requeueAllUnconfirmedTransactions();
            response.put("done", true);
        } catch (RuntimeException e) {
            JSONData.putException(response, e);
        }
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
