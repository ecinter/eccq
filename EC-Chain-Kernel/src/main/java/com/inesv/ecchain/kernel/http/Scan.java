package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class Scan extends APIRequestHandler {

    static final Scan instance = new Scan();

    private Scan() {
        super(new APITag[]{APITag.DEBUG}, "numBlocks", "height", "validate");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        try {
            boolean validate = "true".equalsIgnoreCase(req.getParameter("validate"));
            int numBlocks = 0;
            try {
                numBlocks = Integer.parseInt(req.getParameter("numBlocks"));
            } catch (NumberFormatException ignored) {
            }
            int height = -1;
            try {
                height = Integer.parseInt(req.getParameter("height"));
            } catch (NumberFormatException ignore) {
            }
            long start = System.currentTimeMillis();
            try {
                EcBlockchainProcessorImpl.getInstance().setGetMoreBlocks(false);
                if (numBlocks > 0) {
                    EcBlockchainProcessorImpl.getInstance().scan(EcBlockchainImpl.getInstance().getHeight() - numBlocks + 1, validate);
                } else if (height >= 0) {
                    EcBlockchainProcessorImpl.getInstance().scan(height, validate);
                } else {
                    return JSONResponses.missing("numBlocks", "height");
                }
            } finally {
                EcBlockchainProcessorImpl.getInstance().setGetMoreBlocks(true);
            }
            long end = System.currentTimeMillis();
            response.put("done", true);
            response.put("scanTime", (end - start) / 1000);
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
