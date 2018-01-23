package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.H2.*;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.PeerState;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class TransactionProcessorImpl implements TransactionProcessor {
    private static int maxUnconfirmedTransactions;
    private static final TransactionProcessorImpl instance = new TransactionProcessorImpl();
    private static final Comparator<UnconfirmedTransaction> cachedUnconfirmedTransactionComparator = (UnconfirmedTransaction t1, UnconfirmedTransaction t2) -> {
        int compare;
        // Sort by transaction_height ASC
        compare = Integer.compare(t1.getTransactionHeight(), t2.getTransactionHeight());
        if (compare != 0) {
            return compare;
        }
        // Sort by fee_per_byte DESC
        compare = Long.compare(t1.getFeePerByte(), t2.getFeePerByte());
        if (compare != 0) {
            return -compare;
        }
        // Sort by arrival_timestamp ASC
        compare = Long.compare(t1.getArrivalTimestamp(), t2.getArrivalTimestamp());
        if (compare != 0) {
            return compare;
        }
        // Sort by transaction ID ASC
        return Long.compare(t1.getTransactionId(), t2.getTransactionId());
    };

    @PostConstruct
    public static void initPostConstruct() {
        int n = PropertiesUtil.getKeyForInt("ec.maxUnconfirmedTransactions", 0);
        maxUnconfirmedTransactions = n <= 0 ? Integer.MAX_VALUE : n;
    }


    final H2KeyLongKeyFactory<UnconfirmedTransaction> unconfirmedtransactiondbkeyfactory = new H2KeyLongKeyFactory<UnconfirmedTransaction>("Id") {

        @Override
        public H2Key newKey(UnconfirmedTransaction unconfirmedTransaction) {
            return unconfirmedTransaction.getTransaction().getH2Key();
        }

    };
    private final Map<H2Key, UnconfirmedTransaction> h2KeyUnconfirmedTransactionHashMap = new HashMap<>();
    private final Set<TransactionImpl> broadcastedtransactions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ListenerManager<List<? extends Transaction>, TransactionProcessorEvent> transactionProcessorEventListenerManager = new ListenerManager<>();
    private final PriorityQueue<UnconfirmedTransaction> unconfirmedTransactionPriorityQueue = new PriorityQueue<UnconfirmedTransaction>(
            (UnconfirmedTransaction o1, UnconfirmedTransaction o2) -> {
                int result;
                if ((result = Integer.compare(o2.getTransactionHeight(), o1.getTransactionHeight())) != 0) {
                    return result;
                }
                if ((result = Boolean.compare(o2.getTransaction().referencedTransactionFullHash() != null,
                        o1.getTransaction().referencedTransactionFullHash() != null)) != 0) {
                    return result;
                }
                if ((result = Long.compare(o1.getFeePerByte(), o2.getFeePerByte())) != 0) {
                    return result;
                }
                if ((result = Long.compare(o2.getArrivalTimestamp(), o1.getArrivalTimestamp())) != 0) {
                    return result;
                }
                return Long.compare(o2.getTransactionId(), o1.getTransactionId());
            }) {

        @Override
        public boolean add(UnconfirmedTransaction unconfirmedTransaction) {
            if (!super.add(unconfirmedTransaction)) {
                return false;
            }
            if (size() > maxUnconfirmedTransactions) {
                UnconfirmedTransaction removed = remove();
                //LoggerUtil.logDebug("Dropped unconfirmed transaction " + removed.getJSONObject().toECJSONString());
            }
            return true;
        }

    };
    private final Map<TransactionType, Map<String, Integer>> unconfirmedDuplicates = new HashMap<>();
    private final EntityH2Table<UnconfirmedTransaction> unconfirmedTransactionTable = new EntityH2Table<UnconfirmedTransaction>("unconfirmed_transaction", unconfirmedtransactiondbkeyfactory) {

        @Override
        protected UnconfirmedTransaction load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new UnconfirmedTransaction(rs);
        }

        @Override
        protected void save(Connection con, UnconfirmedTransaction unconfirmedTransaction) throws SQLException {
            unconfirmedTransaction.saveUnconfirmedTransaction(con);
            if (h2KeyUnconfirmedTransactionHashMap.size() < maxUnconfirmedTransactions) {
                h2KeyUnconfirmedTransactionHashMap.put(unconfirmedTransaction.getDbKey(), unconfirmedTransaction);
            }
        }

        @Override
        public void rollback(int height) {
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM unconfirmed_transaction WHERE height > ?")) {
                pstmt.setInt(1, height);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        UnconfirmedTransaction unconfirmedTransaction = load(con, rs, null);
                        unconfirmedTransactionPriorityQueue.add(unconfirmedTransaction);
                        h2KeyUnconfirmedTransactionHashMap.remove(unconfirmedTransaction.getDbKey());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
            super.rollback(height);
            unconfirmedDuplicates.clear();
        }

        @Override
        public void truncate() {
            super.truncate();
            clearCache();
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC, Id ASC ";
        }

    };
    private final Runnable removeUnconfirmedTransactionsThread = () -> {

        try {
            try {
                if (EcBlockchainProcessorImpl.getInstance().isDownloading() && !Constants.TEST_UNCONFIRMED_TRANSACTIONS) {
                    return;
                }
                List<UnconfirmedTransaction> expiredTransactions = new ArrayList<>();
                try (H2Iterator<UnconfirmedTransaction> iterator = unconfirmedTransactionTable.getManyBy(
                        new H2ClauseIntClause("expiration", H2ClauseOp.LT, new EcTime.EpochEcTime().getTime()), 0, -1, "")) {
                    while (iterator.hasNext()) {
                        expiredTransactions.add(iterator.next());
                    }
                }
                if (expiredTransactions.size() > 0) {
                    EcBlockchainImpl.getInstance().writeLock();
                    try {
                        try {
                            H2.H2.beginTransaction();
                            for (UnconfirmedTransaction unconfirmedTransaction : expiredTransactions) {
                                removeUnconfirmedTransaction(unconfirmedTransaction.getTransaction());
                            }
                            H2.H2.commitTransaction();
                        } catch (Exception e) {
                            LoggerUtil.logError(e.toString(), e);
                            H2.H2.rollbackTransaction();
                            throw e;
                        } finally {
                            H2.H2.endTransaction();
                        }
                    } finally {
                        EcBlockchainImpl.getInstance().writeUnlock();
                    }
                }
            } catch (Exception e) {
                LoggerUtil.logError("Error removing unconfirmed transactions", e);
            }
        } catch (Throwable t) {
            LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };
    private final Runnable rebroadcastTransactionsThread = () -> {

        try {
            try {
                if (EcBlockchainProcessorImpl.getInstance().isDownloading() && !Constants.TEST_UNCONFIRMED_TRANSACTIONS) {
                    return;
                }
                List<Transaction> transactionList = new ArrayList<>();
                int curTime = new EcTime.EpochEcTime().getTime();
                for (TransactionImpl transaction : broadcastedtransactions) {
                    if (transaction.getExpiration() < curTime || TransactionH2.hasTransaction(transaction.getTransactionId())) {
                        broadcastedtransactions.remove(transaction);
                    } else if (transaction.getTimestamp() < curTime - 30) {
                        transactionList.add(transaction);
                    }
                }

                if (transactionList.size() > 0) {
                    Peers.sendToSomePeers(transactionList);
                }

            } catch (Exception e) {
                LoggerUtil.logError("Error in transaction re-broadcasting thread", e);
            }
        } catch (Throwable t) {
            LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };
    private final Runnable processTransactionsThread = () -> {

        try {
            try {
                if (EcBlockchainProcessorImpl.getInstance().isDownloading() && !Constants.TEST_UNCONFIRMED_TRANSACTIONS) {
                    return;
                }
                Peer peer = Peers.getAnyPeer(PeerState.CONNECTED, true);
                if (peer == null) {
                    return;
                }
                JSONObject request = new JSONObject();
                request.put("requestType", "getUnconfirmedTransactions");
                JSONArray exclude = new JSONArray();
                getAllUnconfirmedTransactionIds().forEach(transactionId -> exclude.add(Long.toUnsignedString(transactionId)));
                Collections.sort(exclude);
                request.put("exclude", exclude);
                JSONObject response = peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
                if (response == null) {
                    return;
                }
                JSONArray transactionsData = (JSONArray) response.get("unconfirmedTransactions");
                if (transactionsData == null || transactionsData.size() == 0) {
                    return;
                }
                try {
                    processPeerTransactions(transactionsData);
                } catch (EcValidationException | RuntimeException e) {
                    peer.blacklist(e);
                }
            } catch (Exception e) {
                LoggerUtil.logError("Error processing unconfirmed transactions", e);
            }
        } catch (Throwable t) {
            LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };
    private final Runnable processWaitingTransactionsThread = () -> {

        try {
            try {
                if (EcBlockchainProcessorImpl.getInstance().isDownloading() && !Constants.TEST_UNCONFIRMED_TRANSACTIONS) {
                    return;
                }
                processWaitingTransactions();
            } catch (Exception e) {
                LoggerUtil.logError("Error processing waiting transactions", e);
            }
        } catch (Throwable t) {
            LoggerUtil.logError("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };
    private volatile boolean cacheInitialized = false;


    public TransactionProcessorImpl() {
        if (!Constants.IS_LIGHT_CLIENT) {
            if (!Constants.IS_OFFLINE) {
                ThreadPool.scheduleThread("ProcessTransactions", processTransactionsThread, 5);
                ThreadPool.runAfterStart(this::rebroadcastAllUnconfirmedTransactions);
                ThreadPool.scheduleThread("RebroadcastTransactions", rebroadcastTransactionsThread, 23);
            }
            ThreadPool.scheduleThread("RemoveUnconfirmedTransactions", removeUnconfirmedTransactionsThread, 20);
            ThreadPool.scheduleThread("ProcessWaitingTransactions", processWaitingTransactionsThread, 1);
        }
    }

    public static TransactionProcessorImpl getInstance() {
        return instance;
    }

    @Override
    public boolean addECListener(Listener<List<? extends Transaction>> listener, TransactionProcessorEvent eventType) {
        return transactionProcessorEventListenerManager.addECListener(listener, eventType);
    }

    @Override
    public boolean removeECListener(Listener<List<? extends Transaction>> listener, TransactionProcessorEvent eventType) {
        return transactionProcessorEventListenerManager.removeECListener(listener, eventType);
    }

    void notifyListeners(List<? extends Transaction> transactions, TransactionProcessorEvent eventType) {
        transactionProcessorEventListenerManager.notify(transactions, eventType);
    }

    @Override
    public H2Iterator<UnconfirmedTransaction> getAllUnconfirmedTransactions() {
        return unconfirmedTransactionTable.getAll(0, -1);
    }

    @Override
    public H2Iterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(int from, int to) {
        return unconfirmedTransactionTable.getAll(from, to);
    }

    @Override
    public Transaction getUnconfirmedTransaction(long transactionId) {
        H2Key h2Key = unconfirmedtransactiondbkeyfactory.newKey(transactionId);
        return getUnconfirmedTransaction(h2Key);
    }

    Transaction getUnconfirmedTransaction(H2Key h2Key) {
        EcBlockchainImpl.getInstance().readECLock();
        try {
            Transaction transaction = h2KeyUnconfirmedTransactionHashMap.get(h2Key);
            if (transaction != null) {
                return transaction;
            }
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return unconfirmedTransactionTable.get(h2Key);
    }

    private List<Long> getAllUnconfirmedTransactionIds() {
        List<Long> result = new ArrayList<>();
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM unconfirmed_transaction");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getLong("Id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public UnconfirmedTransaction[] getAllWaitingTransactions() {
        UnconfirmedTransaction[] transactions;
        EcBlockchainImpl.getInstance().readECLock();
        try {
            transactions = unconfirmedTransactionPriorityQueue.toArray(new UnconfirmedTransaction[unconfirmedTransactionPriorityQueue.size()]);
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        Arrays.sort(transactions, unconfirmedTransactionPriorityQueue.comparator());
        return transactions;
    }

    Collection<UnconfirmedTransaction> getUnconfirmedTransactionPriorityQueue() {
        return Collections.unmodifiableCollection(unconfirmedTransactionPriorityQueue);
    }

    @Override
    public TransactionImpl[] getAllBroadcastedTransactions() {
        EcBlockchainImpl.getInstance().readECLock();
        try {
            return broadcastedtransactions.toArray(new TransactionImpl[broadcastedtransactions.size()]);
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
    }

    @Override
    public void broadcast(Transaction transaction) throws EcValidationException {
        EcBlockchainImpl.getInstance().writeLock();
        try {
            if (TransactionH2.hasTransaction(transaction.getTransactionId())) {
                LoggerUtil.logInfo("Transaction " + transaction.getStringId() + " already in EC_BLOCKCHAIN, will not broadcast again");
                return;
            }
            if (getUnconfirmedTransaction(((TransactionImpl) transaction).getH2Key()) != null) {
                if (Constants.ENABLE_TRANSACTION_REBROADCASTING) {
                    broadcastedtransactions.add((TransactionImpl) transaction);
                    LoggerUtil.logInfo("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will re-broadcast");
                } else {
                    LoggerUtil.logInfo("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will not broadcast again");
                }
                return;
            }
            transaction.validate();
            UnconfirmedTransaction unconfirmedTransaction = new UnconfirmedTransaction((TransactionImpl) transaction, System.currentTimeMillis());
            boolean broadcastLater = EcBlockchainProcessorImpl.getInstance().isProcessingBlock();
            if (broadcastLater) {
                unconfirmedTransactionPriorityQueue.add(unconfirmedTransaction);
                broadcastedtransactions.add((TransactionImpl) transaction);
                LoggerUtil.logDebug("Will broadcast new transaction later " + transaction.getStringId());
            } else {
                processTransaction(unconfirmedTransaction);
                LoggerUtil.logDebug("Accepted new transaction " + transaction.getStringId());
                List<Transaction> acceptedTransactions = Collections.singletonList(transaction);
                Peers.sendToSomePeers(acceptedTransactions);
                transactionProcessorEventListenerManager.notify(acceptedTransactions, TransactionProcessorEvent.ADDED_UNCONFIRMED_TRANSACTIONS);
                if (Constants.ENABLE_TRANSACTION_REBROADCASTING) {
                    broadcastedtransactions.add((TransactionImpl) transaction);
                }
            }
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void processPeerTransactions(JSONObject request) throws EcValidationException {
        JSONArray transactionsData = (JSONArray) request.get("transactions");
        processPeerTransactions(transactionsData);
    }

    @Override
    public void clearUnconfirmedTransactions() {
        EcBlockchainImpl.getInstance().writeLock();
        try {
            List<Transaction> removed = new ArrayList<>();
            try {
                H2.H2.beginTransaction();
                try (H2Iterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions()) {
                    for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                        unconfirmedTransaction.getTransaction().undoUnconfirmed();
                        removed.add(unconfirmedTransaction.getTransaction());
                    }
                }
                unconfirmedTransactionTable.truncate();
                H2.H2.commitTransaction();
            } catch (Exception e) {
                LoggerUtil.logError(e.toString(), e);
                H2.H2.rollbackTransaction();
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
            unconfirmedDuplicates.clear();
            unconfirmedTransactionPriorityQueue.clear();
            broadcastedtransactions.clear();
            h2KeyUnconfirmedTransactionHashMap.clear();
            transactionProcessorEventListenerManager.notify(removed, TransactionProcessorEvent.REMOVED_UNCONFIRMED_TRANSACTIONS);
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void requeueAllUnconfirmedTransactions() {
        EcBlockchainImpl.getInstance().writeLock();
        try {
            if (!H2.H2.isInTransaction()) {
                try {
                    H2.H2.beginTransaction();
                    requeueAllUnconfirmedTransactions();
                    H2.H2.commitTransaction();
                } catch (Exception e) {
                    LoggerUtil.logError(e.toString(), e);
                    H2.H2.rollbackTransaction();
                    throw e;
                } finally {
                    H2.H2.endTransaction();
                }
                return;
            }
            List<Transaction> removed = new ArrayList<>();
            try (H2Iterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions()) {
                for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                    unconfirmedTransaction.getTransaction().undoUnconfirmed();
                    if (removed.size() < maxUnconfirmedTransactions) {
                        removed.add(unconfirmedTransaction.getTransaction());
                    }
                    unconfirmedTransactionPriorityQueue.add(unconfirmedTransaction);
                }
            }
            unconfirmedTransactionTable.truncate();
            unconfirmedDuplicates.clear();
            h2KeyUnconfirmedTransactionHashMap.clear();
            transactionProcessorEventListenerManager.notify(removed, TransactionProcessorEvent.REMOVED_UNCONFIRMED_TRANSACTIONS);
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void rebroadcastAllUnconfirmedTransactions() {
        EcBlockchainImpl.getInstance().writeLock();
        try {
            try (H2Iterator<UnconfirmedTransaction> oldNonBroadcastedTransactions = getAllUnconfirmedTransactions()) {
                for (UnconfirmedTransaction unconfirmedTransaction : oldNonBroadcastedTransactions) {
                    if (unconfirmedTransaction.getTransaction().isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                        LoggerUtil.logDebug("Skipping duplicate unconfirmed transaction " + unconfirmedTransaction.getTransaction().getJSONObject().toString());
                    } else if (Constants.ENABLE_TRANSACTION_REBROADCASTING) {
                        broadcastedtransactions.add(unconfirmedTransaction.getTransaction());
                    }
                }
            }
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    void removeUnconfirmedTransaction(TransactionImpl transaction) {
        if (!H2.H2.isInTransaction()) {
            try {
                H2.H2.beginTransaction();
                removeUnconfirmedTransaction(transaction);
                H2.H2.commitTransaction();
            } catch (Exception e) {
                LoggerUtil.logError(e.toString(), e);
                H2.H2.rollbackTransaction();
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
            return;
        }
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("DELETE FROM unconfirmed_transaction WHERE id = ?")) {
            pstmt.setLong(1, transaction.getTransactionId());
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                transaction.undoUnconfirmed();
                h2KeyUnconfirmedTransactionHashMap.remove(transaction.getH2Key());
                transactionProcessorEventListenerManager.notify(Collections.singletonList(transaction), TransactionProcessorEvent.REMOVED_UNCONFIRMED_TRANSACTIONS);
            }
        } catch (SQLException e) {
            LoggerUtil.logError(e.toString(), e);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void processLater(Collection<? extends Transaction> transactions) {
        long currentTime = System.currentTimeMillis();
        EcBlockchainImpl.getInstance().writeLock();
        try {
            for (Transaction transaction : transactions) {
                EcBlockH2.transactionCache.remove(transaction.getTransactionId());
                if (TransactionH2.hasTransaction(transaction.getTransactionId())) {
                    continue;
                }
                ((TransactionImpl) transaction).unsetBlock();
                unconfirmedTransactionPriorityQueue.add(new UnconfirmedTransaction((TransactionImpl) transaction, Math.min(currentTime, Convert.fromepochtime(transaction.getTimestamp()))));
            }
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    void processWaitingTransactions() {
        EcBlockchainImpl.getInstance().writeLock();
        try {
            if (unconfirmedTransactionPriorityQueue.size() > 0) {
                int currentTime = new EcTime.EpochEcTime().getTime();
                List<Transaction> addedUnconfirmedTransactions = new ArrayList<>();
                Iterator<UnconfirmedTransaction> iterator = unconfirmedTransactionPriorityQueue.iterator();
                while (iterator.hasNext()) {
                    UnconfirmedTransaction unconfirmedTransaction = iterator.next();
                    try {
                        unconfirmedTransaction.validate();
                        processTransaction(unconfirmedTransaction);
                        iterator.remove();
                        addedUnconfirmedTransactions.add(unconfirmedTransaction.getTransaction());
                    } catch (EcExistingTransactionExceptionEcEc e) {
                        iterator.remove();
                    } catch (EcNotCurrentlyValidExceptionEc e) {
                        if (unconfirmedTransaction.getExpiration() < currentTime
                                || currentTime - Convert.toepochtime(unconfirmedTransaction.getArrivalTimestamp()) > 3600) {
                            iterator.remove();
                        }
                    } catch (EcValidationException | RuntimeException e) {
                        iterator.remove();
                    }
                }
                if (addedUnconfirmedTransactions.size() > 0) {
                    transactionProcessorEventListenerManager.notify(addedUnconfirmedTransactions, TransactionProcessorEvent.ADDED_UNCONFIRMED_TRANSACTIONS);
                }
            }
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    private void processPeerTransactions(JSONArray transactionsData) throws EcNotValidExceptionEc {
        if (EcBlockchainImpl.getInstance().getHeight() <= Constants.EC_LAST_KNOWN_BLOCK && !Constants.TEST_UNCONFIRMED_TRANSACTIONS) {
            return;
        }
        if (transactionsData == null || transactionsData.isEmpty()) {
            return;
        }
        long arrivalTimestamp = System.currentTimeMillis();
        List<TransactionImpl> receivedTransactions = new ArrayList<>();
        List<TransactionImpl> sendToPeersTransactions = new ArrayList<>();
        List<TransactionImpl> addedUnconfirmedTransactions = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        for (Object transactionData : transactionsData) {
            try {
                TransactionImpl transaction = TransactionImpl.parseTransaction((JSONObject) transactionData);
                receivedTransactions.add(transaction);
                if (getUnconfirmedTransaction(transaction.getH2Key()) != null || TransactionH2.hasTransaction(transaction.getTransactionId())) {
                    continue;
                }
                transaction.validate();
                UnconfirmedTransaction unconfirmedTransaction = new UnconfirmedTransaction(transaction, arrivalTimestamp);
                processTransaction(unconfirmedTransaction);
                if (broadcastedtransactions.contains(transaction)) {
                    LoggerUtil.logDebug("Received back transaction " + transaction.getStringId()
                            + " that we broadcasted, will not forward again to peers");
                } else {
                    sendToPeersTransactions.add(transaction);
                }
                addedUnconfirmedTransactions.add(transaction);

            } catch (EcNotCurrentlyValidExceptionEc ignore) {
            } catch (EcValidationException | RuntimeException e) {
                LoggerUtil.logError(String.format("Invalid transaction from peer: %s", ((JSONObject) transactionData).toJSONString()), e);
                exceptions.add(e);
            }
        }
        if (sendToPeersTransactions.size() > 0) {
            Peers.sendToSomePeers(sendToPeersTransactions);
        }
        if (addedUnconfirmedTransactions.size() > 0) {
            transactionProcessorEventListenerManager.notify(addedUnconfirmedTransactions, TransactionProcessorEvent.ADDED_UNCONFIRMED_TRANSACTIONS);
        }
        broadcastedtransactions.removeAll(receivedTransactions);
        if (!exceptions.isEmpty()) {
            throw new EcNotValidExceptionEc("Peer sends invalid transactions: " + exceptions.toString());
        }
    }

    private void processTransaction(UnconfirmedTransaction unconfirmedTransaction) throws EcValidationException {
        TransactionImpl transaction = unconfirmedTransaction.getTransaction();
        int curTime = new EcTime.EpochEcTime().getTime();
        if (transaction.getTimestamp() > curTime + Constants.EC_MAX_TIMEDRIFT || transaction.getExpiration() < curTime) {
            throw new EcNotCurrentlyValidExceptionEc("Invalid transaction timestamp");
        }
        if (transaction.getVersion() < 1) {
            throw new EcNotValidExceptionEc("Invalid transaction version");
        }
        if (transaction.getTransactionId() == 0L) {
            throw new EcNotValidExceptionEc("Invalid transaction Id 0");
        }

        EcBlockchainImpl.getInstance().writeLock();
        try {
            try {
                H2.H2.beginTransaction();
                if (EcBlockchainImpl.getInstance().getHeight() <= Constants.EC_LAST_KNOWN_BLOCK && !Constants.TEST_UNCONFIRMED_TRANSACTIONS) {
                    throw new EcNotCurrentlyValidExceptionEc("EcBlockchain not ready to accept transactions");
                }

                if (getUnconfirmedTransaction(transaction.getH2Key()) != null || TransactionH2.hasTransaction(transaction.getTransactionId())) {
                    throw new EcExistingTransactionExceptionEcEc("Transaction already processed");
                }

                if (!transaction.verifySignature()) {
                    if (Account.getAccount(transaction.getSenderId()) != null) {
                        throw new EcNotValidExceptionEc("Transaction signature verification failed");
                    } else {
                        throw new EcNotCurrentlyValidExceptionEc("Unknown transaction sender");
                    }
                }

                if (!transaction.applyUnconfirmed()) {
                    throw new EcInsufficientBalanceExceptionEcEc("Insufficient balance");
                }

                if (transaction.isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                    throw new EcNotCurrentlyValidExceptionEc("Duplicate unconfirmed transaction");
                }

                unconfirmedTransactionTable.insert(unconfirmedTransaction);

                H2.H2.commitTransaction();
            } catch (Exception e) {
                H2.H2.rollbackTransaction();
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
        } finally {
            EcBlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public SortedSet<? extends Transaction> getCachedUnconfirmedTransactions(List<String> exclude) {
        SortedSet<UnconfirmedTransaction> transactionSet = new TreeSet<>(cachedUnconfirmedTransactionComparator);
        EcBlockchainImpl.getInstance().readECLock();
        try {
            //
            // Initialize the unconfirmed transaction cache if it hasn't been done yet
            //
            synchronized (h2KeyUnconfirmedTransactionHashMap) {
                if (!cacheInitialized) {
                    H2Iterator<UnconfirmedTransaction> it = getAllUnconfirmedTransactions();
                    while (it.hasNext()) {
                        UnconfirmedTransaction unconfirmedTransaction = it.next();
                        h2KeyUnconfirmedTransactionHashMap.put(unconfirmedTransaction.getDbKey(), unconfirmedTransaction);
                    }
                    cacheInitialized = true;
                }
            }
            //
            // Build the result set
            //
            h2KeyUnconfirmedTransactionHashMap.values().forEach(transaction -> {
                if (Collections.binarySearch(exclude, transaction.getStringId()) < 0) {
                    transactionSet.add(transaction);
                }
            });
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return transactionSet;
    }

    @Override
    public List<Transaction> restorePrunableData(JSONArray transactions) throws EcNotValidExceptionEc {
        List<Transaction> processed = new ArrayList<>();
        EcBlockchainImpl.getInstance().readECLock();
        try {
            H2.H2.beginTransaction();
            try {
                //
                // Check each transaction returned by the archive peer
                //
                for (Object transactionJSON : transactions) {
                    TransactionImpl transaction = TransactionImpl.parseTransaction((JSONObject) transactionJSON);
                    TransactionImpl myTransaction = TransactionH2.selectTransactionByFullHash(transaction.fullHash());
                    if (myTransaction != null) {
                        boolean foundAllData = true;
                        //
                        // Process each prunable appendage
                        //
                        appendageLoop:
                        for (AbstractEnclosure appendage : transaction.getAppendages()) {
                            if ((appendage instanceof Prunable)) {
                                //
                                // Don't load the prunable data if we already have the data
                                //
                                for (AbstractEnclosure myAppendage : myTransaction.getAppendages()) {
                                    if (myAppendage.getClass() == appendage.getClass()) {
                                        myAppendage.loadPrunable(myTransaction, true);
                                        if (((Prunable) myAppendage).hasPrunableData()) {
                                            LoggerUtil.logDebug(String.format("Already have prunable data for transaction %s %s appendage",
                                                    myTransaction.getStringId(), myAppendage.getAppendixName()));
                                            continue appendageLoop;
                                        }
                                        break;
                                    }
                                }
                                //
                                // Load the prunable data
                                //
                                if (((Prunable) appendage).hasPrunableData()) {
                                    LoggerUtil.logDebug(String.format("Loading prunable data for transaction %s %s appendage",
                                            Long.toUnsignedString(transaction.getTransactionId()), appendage.getAppendixName()));
                                    ((Prunable) appendage).restorePrunableData(transaction, myTransaction.getBlockTimestamp(), myTransaction.getTransactionHeight());
                                } else {
                                    foundAllData = false;
                                }
                            }
                        }
                        if (foundAllData) {
                            processed.add(myTransaction);
                        }
                        H2.H2.clearCache();
                        H2.H2.commitTransaction();
                    }
                }
                H2.H2.commitTransaction();
            } catch (Exception e) {
                H2.H2.rollbackTransaction();
                processed.clear();
                throw e;
            } finally {
                H2.H2.endTransaction();
            }
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return processed;
    }
}
