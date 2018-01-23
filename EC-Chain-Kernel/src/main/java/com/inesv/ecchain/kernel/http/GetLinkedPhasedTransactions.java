package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.PhasingPoll;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public class GetLinkedPhasedTransactions extends APIRequestHandler {
    static final GetLinkedPhasedTransactions instance = new GetLinkedPhasedTransactions();

    private GetLinkedPhasedTransactions() {
        super(new APITag[]{APITag.PHASING}, "linkedFullHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        byte[] linkedFullHash = ParameterParser.getBytes(req, "linkedFullHash", true);

        JSONArray json = new JSONArray();
        List<? extends Transaction> transactions = PhasingPoll.getLinkedPhasedTransactions(linkedFullHash);
        transactions.forEach(transaction -> json.add(JSONData.transaction(transaction)));
        JSONObject response = new JSONObject();
        response.put("transactions", json);

        return response;
    }
}