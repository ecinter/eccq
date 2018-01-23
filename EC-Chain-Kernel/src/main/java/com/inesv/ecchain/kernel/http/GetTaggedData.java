package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.kernel.core.BadgeData;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.PRUNED_TRANSACTION;

public final class GetTaggedData extends APIRequestHandler {

    static final GetTaggedData instance = new GetTaggedData();

    private GetTaggedData() {
        super(new APITag[]{APITag.DATA}, "transaction", "includeData", "retrieve");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        boolean includeData = !"false".equalsIgnoreCase(req.getParameter("includeData"));
        boolean retrieve = "true".equalsIgnoreCase(req.getParameter("retrieve"));

        BadgeData badgeData = BadgeData.getData(transactionId);
        if (badgeData == null && retrieve) {
            if (EcBlockchainProcessorImpl.getInstance().restorePrunedTransaction(transactionId) == null) {
                return PRUNED_TRANSACTION;
            }
            badgeData = BadgeData.getData(transactionId);
        }
        if (badgeData != null) {
            return JSONData.taggedData(badgeData, includeData);
        }
        return JSON.EMPTY_JSON;
    }

}
