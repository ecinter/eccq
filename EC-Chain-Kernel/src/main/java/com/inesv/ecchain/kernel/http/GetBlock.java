package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.EcBlock;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class GetBlock extends APIRequestHandler {

    static final GetBlock instance = new GetBlock();

    private GetBlock() {
        super(new APITag[]{APITag.BLOCKS}, "block", "height", "timestamp", "includeTransactions", "includeExecutedPhased");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        EcBlock ecBlockData;
        String blockValue = Convert.emptyToNull(req.getParameter("block"));
        String heightValue = Convert.emptyToNull(req.getParameter("height"));
        String timestampValue = Convert.emptyToNull(req.getParameter("timestamp"));
        if (blockValue != null) {
            try {
                ecBlockData = EcBlockchainImpl.getInstance().getBlock(Convert.parseUnsignedLong(blockValue));
            } catch (RuntimeException e) {
                return INCORRECT_BLOCK;
            }
        } else if (heightValue != null) {
            try {
                int height = Integer.parseInt(heightValue);
                if (height < 0 || height > EcBlockchainImpl.getInstance().getHeight()) {
                    return INCORRECT_HEIGHT;
                }
                ecBlockData = EcBlockchainImpl.getInstance().getBlockAtHeight(height);
            } catch (RuntimeException e) {
                return INCORRECT_HEIGHT;
            }
        } else if (timestampValue != null) {
            try {
                int timestamp = Integer.parseInt(timestampValue);
                if (timestamp < 0) {
                    return INCORRECT_TIMESTAMP;
                }
                ecBlockData = EcBlockchainImpl.getInstance().getLastECBlock(timestamp);
            } catch (RuntimeException e) {
                return INCORRECT_TIMESTAMP;
            }
        } else {
            ecBlockData = EcBlockchainImpl.getInstance().getLastECBlock();
        }

        if (ecBlockData == null) {
            return UNKNOWN_BLOCK;
        }

        boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));
        boolean includeExecutedPhased = "true".equalsIgnoreCase(req.getParameter("includeExecutedPhased"));

        return JSONData.block(ecBlockData, includeTransactions, includeExecutedPhased);

    }

}