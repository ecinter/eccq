package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class PopOff extends APIRequestHandler {

    static final PopOff instance = new PopOff();

    private PopOff() {
        super(new APITag[]{APITag.DEBUG}, "numBlocks", "height", "keepTransactions");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        int numBlocks = 0;
        try {
            numBlocks = Integer.parseInt(req.getParameter("numBlocks"));
        } catch (NumberFormatException ignored) {
        }
        int height = 0;
        try {
            height = Integer.parseInt(req.getParameter("height"));
        } catch (NumberFormatException ignored) {
        }
        boolean keepTransactions = "true".equalsIgnoreCase(req.getParameter("keepTransactions"));
        List<? extends EcBlock> blocks;
        try {
            EcBlockchainProcessorImpl.getInstance().setGetMoreBlocks(false);
            if (numBlocks > 0) {
                blocks = EcBlockchainProcessorImpl.getInstance().popOffTo(EcBlockchainImpl.getInstance().getHeight() - numBlocks);
            } else if (height > 0) {
                blocks = EcBlockchainProcessorImpl.getInstance().popOffTo(height);
            } else {
                return JSONResponses.missing("numBlocks", "height");
            }
        } finally {
            EcBlockchainProcessorImpl.getInstance().setGetMoreBlocks(true);
        }
        JSONArray blocksJSON = new JSONArray();
        blocks.forEach(block -> blocksJSON.add(JSONData.block(block, true, false)));
        JSONObject response = new JSONObject();
        response.put("blocks", blocksJSON);
        if (keepTransactions) {
            blocks.forEach(block -> TransactionProcessorImpl.getInstance().processLater(block.getTransactions()));
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
