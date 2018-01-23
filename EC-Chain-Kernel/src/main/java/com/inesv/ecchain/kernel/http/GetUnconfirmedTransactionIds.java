package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.core.TransactionProcessor;
import com.inesv.ecchain.kernel.core.TransactionProcessorImpl;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.FilteringIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;

public final class GetUnconfirmedTransactionIds extends APIRequestHandler {

    static final GetUnconfirmedTransactionIds instance = new GetUnconfirmedTransactionIds();

    private GetUnconfirmedTransactionIds() {
        super(new APITag[]{APITag.TRANSACTIONS, APITag.ACCOUNTS}, "account", "account", "account", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray transactionIds = new JSONArray();
        if (accountIds.isEmpty()) {
            try (H2Iterator<? extends Transaction> transactionsIterator = TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions(firstIndex, lastIndex)) {
                while (transactionsIterator.hasNext()) {
                    Transaction transaction = transactionsIterator.next();
                    transactionIds.add(transaction.getStringId());
                }
            }
        } else {
            TransactionProcessor tp = TransactionProcessorImpl.getInstance();
            try (FilteringIterator<? extends Transaction> transactionsIterator = new FilteringIterator<>(tp.getAllUnconfirmedTransactions(0, -1),
                    transaction -> accountIds.contains(transaction.getSenderId()) || accountIds.contains(transaction.getRecipientId()),
                    firstIndex, lastIndex)) {
                while (transactionsIterator.hasNext()) {
                    Transaction transaction = transactionsIterator.next();
                    transactionIds.add(transaction.getStringId());
                }
            }
        }

        JSONObject response = new JSONObject();
        response.put("unconfirmedTransactionIds", transactionIds);
        return response;
    }

}
