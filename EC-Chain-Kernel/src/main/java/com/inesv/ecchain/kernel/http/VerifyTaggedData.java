package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import com.inesv.ecchain.kernel.core.Transaction;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class VerifyTaggedData extends APIRequestHandler {

    static final VerifyTaggedData instance = new VerifyTaggedData();

    private VerifyTaggedData() {
        super("file", new APITag[]{APITag.DATA}, "transaction",
                "name", "description", "tags", "type", "channel", "isText", "filename", "data");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        Transaction transaction = EcBlockchainImpl.getInstance().getTransaction(transactionId);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }

        Mortgaged.TaggedDataUpload taggedData = ParameterParser.getTaggedData(req);
        Mortgaged mortgaged = transaction.getAttachment();

        if (!(mortgaged instanceof Mortgaged.TaggedDataUpload)) {
            return INCORRECT_TRANSACTION;
        }

        Mortgaged.TaggedDataUpload myTaggedData = (Mortgaged.TaggedDataUpload) mortgaged;
        if (!Arrays.equals(myTaggedData.getHash(), taggedData.getHash())) {
            return HASHES_MISMATCH;
        }

        JSONObject response = myTaggedData.getJSONObject();
        response.put("ecVerify", true);
        return response;
    }

}
