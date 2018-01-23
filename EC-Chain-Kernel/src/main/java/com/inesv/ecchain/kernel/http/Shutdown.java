package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class Shutdown extends APIRequestHandler {

    static final Shutdown instance = new Shutdown();

    private Shutdown() {
        super(new APITag[]{APITag.DEBUG}, "scan");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        boolean scan = "true".equalsIgnoreCase(req.getParameter("scan"));
        if (scan) {
            EcBlockchainProcessorImpl.getInstance().fullScanWithShutdown();
        } else {
            new Thread(() -> System.exit(0)).start();
        }
        response.put("shutdown", true);
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
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
