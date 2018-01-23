package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_TRANSACTION;


public final class ExtendTaggedData extends CreateTransaction {

    static final ExtendTaggedData instance = new ExtendTaggedData();

    private ExtendTaggedData() {
        super("file", new APITag[]{APITag.DATA, APITag.CREATE_TRANSACTION}, "transaction",
                "name", "description", "tags", "type", "channel", "isText", "filename", "data");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Account account = ParameterParser.getSenderAccount(req);
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        BadgeData badgeData = BadgeData.getData(transactionId);
        if (badgeData == null) {
            Transaction transaction = EcBlockchainImpl.getInstance().getTransaction(transactionId);
            if (transaction == null || transaction.getTransactionType() != Data.TAGGED_DATA_UPLOAD) {
                return UNKNOWN_TRANSACTION;
            }
            Mortgaged.TaggedDataUpload taggedDataUpload = ParameterParser.getTaggedData(req);
            badgeData = new BadgeData(transaction, taggedDataUpload);
        }
        Mortgaged.TaggedDataExtend taggedDataExtend = new Mortgaged.TaggedDataExtend(badgeData);
        return createTransaction(req, account, taggedDataExtend);

    }

}
