package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public final class GetTaggedDataExtendTransactions extends APIRequestHandler {

    static final GetTaggedDataExtendTransactions instance = new GetTaggedDataExtendTransactions();

    private GetTaggedDataExtendTransactions() {
        super(new APITag[]{APITag.DATA}, "transaction");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long taggedDataId = ParameterParser.getUnsignedLong(req, "transaction", true);
        List<Long> extendTransactions = BadgeData.getExtendTransactionIds(taggedDataId);
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        EcBlockchain ecBlockchain = EcBlockchainImpl.getInstance();
        Filter<Enclosure> filter = (appendix) -> !(appendix instanceof Mortgaged.TaggedDataExtend);
        extendTransactions.forEach(transactionId -> jsonArray.add(JSONData.transaction(ecBlockchain.getTransaction(transactionId), filter)));
        response.put("extendTransactions", jsonArray);
        return response;
    }

}
