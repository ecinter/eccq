package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.Listener;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.H2.TransactionCallback;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.Peers;
import com.inesv.ecchain.kernel.peer.PeersEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;


class EventListener implements Runnable, AsyncListener, TransactionCallback {

    static final EcBlockchainProcessor EC_BLOCKCHAIN_PROCESSOR = EcBlockchainProcessorImpl.getInstance();

    static final TransactionProcessor TRANSACTION_PROCESSOR = TransactionProcessorImpl.getInstance();

    static final Map<String, EventListener> EVENT_LISTENERS = new ConcurrentHashMap<>();

    static final List<PeersEvent> PEER_EVENTS = new ArrayList<>();

    static final List<EcBlockchainProcessorEvent> BLOCK_EVENTS = new ArrayList<>();

    static final List<TransactionProcessorEvent> TX_EVENTS = new ArrayList<>();

    static final List<AccountLedgerEvent> EVENT_LIST = new ArrayList<>();

    private static final Timer EVENT_TIMER = new Timer();

    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private final ReentrantLock lock = new ReentrantLock();

    private final List<EcEventListener> ecEventListeners = new ArrayList<>();

    private final List<PendingEvent> pendingEvents = new ArrayList<>();

    private final List<PendingEvent> H2Events = new ArrayList<>();

    private final List<AsyncContext> pendingWaits = new ArrayList<>();

    private final String address;

    private long timestamp;

    private volatile boolean deactivated;

    private boolean aborted;

    private boolean dispatched;

    static {
        EVENT_TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                long oldestTime = System.currentTimeMillis() - Constants.EVENT_TIMEOUT * 1000;
                EVENT_LISTENERS.values().forEach(listener -> {
                    if (listener.getTimestamp() < oldestTime) {
                        listener.deactivateListener();
                    }
                });
            }
        }, Constants.EVENT_TIMEOUT * 1000 / 2, Constants.EVENT_TIMEOUT * 1000 / 2);

        PEER_EVENTS.add(PeersEvent.ADD_INBOUND);
        PEER_EVENTS.add(PeersEvent.ADDED_ACTIVE_PEER);
        PEER_EVENTS.add(PeersEvent.BLACKLIST);
        PEER_EVENTS.add(PeersEvent.CHANGED_ACTIVE_PEER);
        PEER_EVENTS.add(PeersEvent.DEACTIVATE);
        PEER_EVENTS.add(PeersEvent.NEW_PEER);
        PEER_EVENTS.add(PeersEvent.REMOVE);
        PEER_EVENTS.add(PeersEvent.REMOVE_INBOUND);
        PEER_EVENTS.add(PeersEvent.UNBLACKLIST);
        BLOCK_EVENTS.add(EcBlockchainProcessorEvent.BLOCK_GENERATED);
        BLOCK_EVENTS.add(EcBlockchainProcessorEvent.BLOCK_POPPED);
        BLOCK_EVENTS.add(EcBlockchainProcessorEvent.BLOCK_PUSHED);
        TX_EVENTS.add(TransactionProcessorEvent.ADDED_CONFIRMED_TRANSACTIONS);
        TX_EVENTS.add(TransactionProcessorEvent.ADDED_UNCONFIRMED_TRANSACTIONS);
        TX_EVENTS.add(TransactionProcessorEvent.REJECT_PHASED_TRANSACTION);
        TX_EVENTS.add(TransactionProcessorEvent.RELEASE_PHASED_TRANSACTION);
        TX_EVENTS.add(TransactionProcessorEvent.REMOVED_UNCONFIRMED_TRANSACTIONS);
        EVENT_LIST.add(AccountLedgerEvent.ADD_ENTRY);
    }

    EventListener(String address) {
        this.address = address;
    }

    void activateListener(List<EventRegistration> eventRegistrations) throws EventListenerException {
        if (deactivated)
            throw new EventListenerException("Event listener deactivated");
        if (EVENT_LISTENERS.size() >= Constants.MAX_EVENT_USERS && EVENT_LISTENERS.get(address) == null)
            throw new EventListenerException(String.format("Too many API event users: Maximum %d", Constants.MAX_EVENT_USERS));
        //
        // Start listening for events
        //
        putEvents(eventRegistrations);
        //
        // Add this event listener to the active list
        //
        EventListener oldListener = EVENT_LISTENERS.put(address, this);
        if (oldListener != null)
            oldListener.deactivateListener();
        LoggerUtil.logDebug(String.format("Event listener activated for %s", address));
    }

    void putEvents(List<EventRegistration> eventRegistrations) throws EventListenerException {
        lock.lock();
        try {
            if (deactivated)
                return;
            //
            // A listener with account identifier 0 accepts events for all accounts.
            // This listener supersedes  listeners for a single account.
            //
            for (EventRegistration event : eventRegistrations) {
                boolean addListener = true;
                Iterator<EcEventListener> it = ecEventListeners.iterator();
                while (it.hasNext()) {
                    EcEventListener listener = it.next();
                    if (listener.getEvent() == event.getEvent()) {
                        long accountId = listener.getAccountId();
                        if (accountId == event.getAccountId() || accountId == 0) {
                            addListener = false;
                            break;
                        }
                        if (event.getAccountId() == 0) {
                            listener.removeListener();
                            it.remove();
                        }
                    }
                }
                if (addListener) {
                    EcEventListener listener = new EcEventListener(event);
                    listener.addListener();
                    ecEventListeners.add(listener);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    void delEvents(List<EventRegistration> eventRegistrations) {
        lock.lock();
        try {
            if (deactivated)
                return;
            //
            // Specifying an account identifier of 0 results in removing all
            // listeners for the specified event.  Otherwise, only the listener
            // for the specified account is removed.
            //
            for (EventRegistration event : eventRegistrations) {
                Iterator<EcEventListener> it = ecEventListeners.iterator();
                while (it.hasNext()) {
                    EcEventListener listener = it.next();
                    if (listener.getEvent() == event.getEvent() &&
                            (listener.getAccountId() == event.getAccountId() || event.getAccountId() == 0)) {
                        listener.removeListener();
                        it.remove();
                    }
                }
            }
            //
            // Deactivate the listeners if there are no events remaining
            //
            if (ecEventListeners.isEmpty())
                deactivateListener();
        } finally {
            lock.unlock();
        }
    }

    void deactivateListener() {
        lock.lock();
        try {
            if (deactivated)
                return;
            deactivated = true;
            //
            // Cancel all pending wait requests
            //
            if (!pendingWaits.isEmpty() && !dispatched) {
                dispatched = true;
                THREAD_POOL.submit(this);
            }
            //
            // Remove this event listener from the active list
            //
            EVENT_LISTENERS.remove(address);
            //
            // Stop listening for events
            //
            ecEventListeners.forEach(EcEventListener::removeListener);
        } finally {
            lock.unlock();
        }
        LoggerUtil.logDebug(String.format("Event listener deactivated for %s", address));
    }

    List<PendingEvent> eventWait(HttpServletRequest req, long timeout) throws EventListenerException {
        List<PendingEvent> events = null;
        lock.lock();
        try {
            if (deactivated)
                throw new EventListenerException("Event listener deactivated");
            if (!pendingWaits.isEmpty()) {
                //
                // We want only one waiter at a time.  This can happen if the
                // application issues an event wait while it already has an event
                // wait outstanding.  In this case, we will cancel the current wait
                // and replace it with the new wait.
                //
                aborted = true;
                if (!dispatched) {
                    dispatched = true;
                    THREAD_POOL.submit(this);
                }
                AsyncContext context = req.startAsync();
                context.addListener(this);
                context.setTimeout(timeout * 1000);
                pendingWaits.add(context);
            } else if (!pendingEvents.isEmpty()) {
                //
                // Return immediately if we have a pending event
                //
                events = new ArrayList<>();
                events.addAll(pendingEvents);
                pendingEvents.clear();
                timestamp = System.currentTimeMillis();
            } else {
                //
                // Wait for an event
                //
                aborted = false;
                AsyncContext context = req.startAsync();
                context.addListener(this);
                context.setTimeout(timeout * 1000);
                pendingWaits.add(context);
                timestamp = System.currentTimeMillis();
            }
        } finally {
            lock.unlock();
        }
        return events;
    }

    long getTimestamp() {
        return timestamp;
    }

    @Override
    public void run() {
        lock.lock();
        try {
            dispatched = false;
            while (!pendingWaits.isEmpty() && (aborted || deactivated || !pendingEvents.isEmpty())) {
                AsyncContext context = pendingWaits.remove(0);
                List<PendingEvent> events = new ArrayList<>();
                if (!aborted && !deactivated) {
                    events.addAll(pendingEvents);
                    pendingEvents.clear();
                }
                HttpServletResponse resp = (HttpServletResponse) context.getResponse();
                JSONObject response = EventWait.formatResponse(events);
                response.put("requestProcessingTime", System.currentTimeMillis() - timestamp);
                try (Writer writer = resp.getWriter()) {
                    response.writeJSONString(writer);
                } catch (IOException exc) {
                    LoggerUtil.logDebug(String.format("Unable to return API response to %s: %s",
                            address, exc.toString()));
                }
                context.complete();
                aborted = false;
                timestamp = System.currentTimeMillis();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onComplete(AsyncEvent event) {
    }

    @Override
    public void onError(AsyncEvent event) {
        AsyncContext context = event.getAsyncContext();
        lock.lock();
        try {
            pendingWaits.remove(context);
            context.complete();
            timestamp = System.currentTimeMillis();
            LoggerUtil.logError("Error detected during event wait for " + address, event.getThrowable());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onStartAsync(AsyncEvent event) {
    }

    @Override
    public void onTimeout(AsyncEvent event) {
        AsyncContext context = event.getAsyncContext();
        lock.lock();
        try {
            pendingWaits.remove(context);
            JSONObject response = new JSONObject();
            response.put("events", new JSONArray());
            response.put("requestProcessingTime", System.currentTimeMillis() - timestamp);
            try (Writer writer = context.getResponse().getWriter()) {
                response.writeJSONString(writer);
            } catch (IOException exc) {
                LoggerUtil.logDebug(String.format("Unable to return API response to %s: %s",
                        address, exc.toString()));
            }
            context.complete();
            timestamp = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void commit() {
        Thread thread = Thread.currentThread();
        lock.lock();
        try {
            Iterator<PendingEvent> it = H2Events.iterator();
            while (it.hasNext()) {
                PendingEvent pendingEvent = it.next();
                if (pendingEvent.getThread() == thread) {
                    it.remove();
                    pendingEvents.add(pendingEvent);
                    if (!pendingWaits.isEmpty() && !dispatched) {
                        dispatched = true;
                        THREAD_POOL.submit(EventListener.this);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rollback() {
        Thread thread = Thread.currentThread();
        lock.lock();
        try {
            Iterator<PendingEvent> it = H2Events.iterator();
            while (it.hasNext()) {
                if (it.next().getThread() == thread)
                    it.remove();
            }
        } finally {
            lock.unlock();
        }
    }

    private class EcEventListener {

        /**
         * Event handler
         */
        private final EcEventHandler eventHandler;

        /**
         * Create the Ec event listener
         *
         * @param eventRegistration Event registration
         * @throws EventListenerException Invalid event
         */
        public EcEventListener(EventRegistration eventRegistration) throws EventListenerException {
            Enum<? extends Enum> event = eventRegistration.getEvent();
            if (event instanceof PeersEvent) {
                eventHandler = new PeerEventHandler(eventRegistration);
            } else if (event instanceof EcBlockchainProcessorEvent) {
                eventHandler = new BlockEventHandler(eventRegistration);
            } else if (event instanceof TransactionProcessorEvent) {
                eventHandler = new TransactionEventHandler(eventRegistration);
            } else if (event instanceof AccountLedgerEvent) {
                eventHandler = new LedgerEventHandler(eventRegistration);
            } else {
                throw new EventListenerException("Unsupported listener event");
            }
        }

        /**
         * Return the Ec event
         *
         * @return Ec event
         */
        public Enum<? extends Enum> getEvent() {
            return eventHandler.getEvent();
        }

        /**
         * Return the account identifier
         *
         * @return Account identifier
         */
        public long getAccountId() {
            return eventHandler.getAccountId();
        }

        /**
         * Add the Ec listener for this event
         */
        public void addListener() {
            eventHandler.addListener();
        }

        /**
         * Remove the Ec listener for this event
         */
        public void removeListener() {
            eventHandler.removeListener();
        }

        /**
         * Return the hash code for this Ec event listener
         *
         * @return Hash code
         */
        @Override
        public int hashCode() {
            return eventHandler.hashCode();
        }

        /**
         * Check if two Ec events listeners are equal
         *
         * @param obj Comparison listener
         * @return TRUE if the listeners are equal
         */
        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof EcEventListener) &&
                    eventHandler.equals(((EcEventListener) obj).eventHandler));
        }

        /**
         * Ec listener event handler
         */
        private abstract class EcEventHandler {

            /**
             * Owning event listener
             */
            protected final EventListener owner;

            /**
             * Account identifier
             */
            protected final long accountId;

            /**
             * Ec listener event
             */
            protected final Enum<? extends Enum> event;

            /**
             * Create the Ec event handler
             *
             * @param eventRegistration Event registration
             */
            public EcEventHandler(EventRegistration eventRegistration) {
                this.owner = EventListener.this;
                this.accountId = eventRegistration.getAccountId();
                this.event = eventRegistration.getEvent();
            }

            /**
             * Return the Ec event
             *
             * @return Ec event
             */
            public Enum<? extends Enum> getEvent() {
                return event;
            }

            /**
             * Return the account identifier
             *
             * @return Account identifier
             */
            public long getAccountId() {
                return accountId;
            }

            /**
             * Add the Ec listener for this event
             */
            public abstract void addListener();

            /**
             * Remove the Ec listener for this event
             */
            public abstract void removeListener();

            /**
             * Check if need to wait for end of transaction
             *
             * @return TRUE if need to wait for transaction to commit/rollback
             */
            protected boolean waitTransaction() {
                return true;
            }

            /**
             * Dispatch the event
             */
            protected void dispatch(PendingEvent pendingEvent) {
                lock.lock();
                try {
                    if (waitTransaction() && H2.H2.isInTransaction()) {
                        pendingEvent.setThread(Thread.currentThread());
                        H2Events.add(pendingEvent);
                        H2.H2.registerCallback(owner);
                    } else {
                        pendingEvents.add(pendingEvent);
                        if (!pendingWaits.isEmpty() && !dispatched) {
                            dispatched = true;
                            THREAD_POOL.submit(owner);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }

            /**
             * Return the hash code for this event listener
             *
             * @return Hash code
             */
            @Override
            public int hashCode() {
                return event.hashCode();
            }

            /**
             * Check if two events listeners are equal
             *
             * @param obj Comparison listener
             * @return TRUE if the listeners are equal
             */
            @Override
            public boolean equals(Object obj) {
                return (obj != null && (obj instanceof EcEventHandler) &&
                        owner == ((EcEventHandler) obj).owner &&
                        accountId == ((EcEventHandler) obj).accountId &&
                        event == ((EcEventHandler) obj).event);
            }
        }

        /**
         * Peer event handler
         */
        private class PeerEventHandler extends EcEventHandler implements Listener<Peer> {

            /**
             * Create the peer event handler
             *
             * @param eventRegistration Event registration
             */
            public PeerEventHandler(EventRegistration eventRegistration) {
                super(eventRegistration);
            }

            /**
             * Add the Ec listener for this event
             */
            @Override
            public void addListener() {
                Peers.addPeersListener(this, (PeersEvent) event);
            }

            /**
             * Remove the Ec listener for this event
             */
            @Override
            public void removeListener() {
                Peers.removePeersListener(this, (PeersEvent) event);
            }

            /**
             * Event notification
             *
             * @param peer Peer
             */
            @Override
            public void notify(Peer peer) {
                dispatch(new PendingEvent("Peer." + event.name(), peer.getPeerHost()));
            }

            /**
             * Check if need to wait for end of transaction
             *
             * @return TRUE if need to wait for transaction to commit/rollback
             */
            @Override
            protected boolean waitTransaction() {
                return false;
            }
        }

        /**
         * EcBlockchain processor event handler
         */
        private class BlockEventHandler extends EcEventHandler implements Listener<EcBlock> {

            /**
             * Create the blockchain processor event handler
             *
             * @param eventRegistration Event registration
             */
            public BlockEventHandler(EventRegistration eventRegistration) {
                super(eventRegistration);
            }

            /**
             * Add the Ec listener for this event
             */
            @Override
            public void addListener() {
                EC_BLOCKCHAIN_PROCESSOR.addECListener(this, (EcBlockchainProcessorEvent) event);
            }

            /**
             * Remove the Ec listener for this event
             */
            @Override
            public void removeListener() {
                EC_BLOCKCHAIN_PROCESSOR.removeECListener(this, (EcBlockchainProcessorEvent) event);
            }

            /**
             * Event notification
             *
             * @param ecBlock EcBlock
             */
            @Override
            public void notify(EcBlock ecBlock) {
                dispatch(new PendingEvent("EcBlock." + event.name(), ecBlock.getStringECId()));
            }
        }

        /**
         * Transaction processor event handler
         */
        private class TransactionEventHandler extends EcEventHandler implements Listener<List<? extends Transaction>> {

            /**
             * Create the transaction processor event handler
             *
             * @param eventRegistration Event registration
             */
            public TransactionEventHandler(EventRegistration eventRegistration) {
                super(eventRegistration);
            }

            /**
             * Add the Ec listener for this event
             */
            @Override
            public void addListener() {
                TRANSACTION_PROCESSOR.addECListener(this, (TransactionProcessorEvent) event);
            }

            /**
             * Remove the Ec listener for this event
             */
            @Override
            public void removeListener() {
                TRANSACTION_PROCESSOR.removeECListener(this, (TransactionProcessorEvent) event);
            }

            /**
             * Event notification
             *
             * @param txList Transaction list
             */
            @Override
            public void notify(List<? extends Transaction> txList) {
                List<String> idList = new ArrayList<>();
                txList.forEach((tx) -> idList.add(tx.getStringId()));
                dispatch(new PendingEvent("Transaction." + event.name(), idList));
            }
        }

        /**
         * Account ledger event handler
         */
        private class LedgerEventHandler extends EcEventHandler implements Listener<AccountLedger.LedgerEntry> {

            /**
             * Create the account ledger event handler
             *
             * @param eventRegistration Event registration
             */
            public LedgerEventHandler(EventRegistration eventRegistration) {
                super(eventRegistration);
            }

            /**
             * Add the Ec listener for this event
             */
            @Override
            public void addListener() {
                AccountLedger.addAccountLedgerListener(this, (AccountLedgerEvent) event);
            }

            /**
             * Remove the Ec listener for this event
             */
            @Override
            public void removeListener() {
                AccountLedger.removeAccountLedgerListener(this, (AccountLedgerEvent) event);
            }

            /**
             * Event notification
             *
             * @param entry Ledger entry
             */
            @Override
            public void notify(AccountLedger.LedgerEntry entry) {
                if (entry.getAccountId() == accountId || accountId == 0)
                    dispatch(new PendingEvent(String.format("Ledger.%s.%s",
                            event.name(), Convert.rsAccount(entry.getAccountId())),
                            Long.toUnsignedString(entry.getLedgerId())));
            }
        }
    }
}
