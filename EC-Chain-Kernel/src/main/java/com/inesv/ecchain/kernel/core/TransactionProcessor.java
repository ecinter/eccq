package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Observable;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

public interface TransactionProcessor extends Observable<List<? extends Transaction>, TransactionProcessorEvent> {

    H2Iterator<? extends Transaction> getAllUnconfirmedTransactions();

    H2Iterator<? extends Transaction> getAllUnconfirmedTransactions(int from, int to);

    Transaction getUnconfirmedTransaction(long transactionId);

    Transaction[] getAllWaitingTransactions();

    Transaction[] getAllBroadcastedTransactions();

    void clearUnconfirmedTransactions();

    void requeueAllUnconfirmedTransactions();

    void rebroadcastAllUnconfirmedTransactions();

    void broadcast(Transaction transaction) throws EcValidationException;

    void processPeerTransactions(JSONObject request) throws EcValidationException;

    void processLater(Collection<? extends Transaction> transactions);

    SortedSet<? extends Transaction> getCachedUnconfirmedTransactions(List<String> exclude);

    List<Transaction> restorePrunableData(JSONArray transactions) throws EcNotValidExceptionEc;

}
