package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public final class FundMonitoring {
    private static final Semaphore processSemaphore = new Semaphore(0);
    private static volatile boolean started = false;
    private static volatile boolean stopped = false;
    private static final List<FundMonitoring> monitors = new ArrayList<>();
    private static final Map<Long, List<MonitoredAccount>> accounts = new HashMap<>();
    private static final ConcurrentLinkedQueue<MonitoredAccount> pendingEvents = new ConcurrentLinkedQueue<>();
    private final HoldingType holdingType;
    private final long holdingId;
    private final String property;
    private final long amount;
    private final long threshold;
    private final int interval;
    private final long accountId;
    private final String accountName;
    private final String secretPhrase;
    private final byte[] publicKey;
    private FundMonitoring(HoldingType holdingType, long holdingId, String property,
                           long amount, long threshold, int interval,
                           long accountId, String secretPhrase) {
        this.holdingType = holdingType;
        this.holdingId = (holdingType != HoldingType.EC ? holdingId : 0);
        this.property = property;
        this.amount = amount;
        this.threshold = threshold;
        this.interval = interval;
        this.accountId = accountId;
        this.accountName = Convert.rsAccount(accountId);
        this.secretPhrase = secretPhrase;
        this.publicKey = Crypto.getPublicKey(secretPhrase);
    }
    public static boolean startMonitor(HoldingType holdingType, long holdingId, String property,
                                       long amount, long threshold, int interval, String secretPhrase) {
        //
        // Initialize monitor processing if it hasn't been done yet.  We do this now
        // instead of during NRS initialization so we don't start the monitor thread if it
        // won't be used.
        //
        init();
        long accountId = Account.getId(Crypto.getPublicKey(secretPhrase));
        //
        // Create the monitor
        //
        FundMonitoring monitor = new FundMonitoring(holdingType, holdingId, property,
                amount, threshold, interval, accountId, secretPhrase);
        EcBlockchainImpl.getInstance().readECLock();
        try {
            //
            // Locate monitored accounts based on the account property and the setter identifier
            //
            List<MonitoredAccount> accountList = new ArrayList<>();
            try (H2Iterator<Account.AccountProperty> it = Account.getProperties(0, accountId, property, 0, Integer.MAX_VALUE)) {
                while (it.hasNext()) {
                    Account.AccountProperty accountProperty = it.next();
                    MonitoredAccount account = createMonitoredAccount(accountProperty.getRecipientId(),
                            monitor, accountProperty.getValue());
                    accountList.add(account);
                }
            }
            //
            // Activate the monitor and check each monitored account to see if we need to submit
            // an initial fund transaction
            //
            synchronized (monitors) {
                if (monitors.size() > Constants.EC_MAX_MONITORS) {
                    throw new RuntimeException("Maximum of " + Constants.EC_MAX_MONITORS + " monitors already started");
                }
                if (monitors.contains(monitor)) {
                    LoggerUtil.logDebug(String.format("%s monitor already started for account %s, property '%s', holding %s",
                            holdingType.name(), monitor.accountName, property, Long.toUnsignedString(holdingId)));
                    return false;
                }
                accountList.forEach(account -> {
                    List<MonitoredAccount> activeList = accounts.get(account.accountId);
                    if (activeList == null) {
                        activeList = new ArrayList<>();
                        accounts.put(account.accountId, activeList);
                    }
                    activeList.add(account);
                    pendingEvents.add(account);
                    LoggerUtil.logDebug(String.format("Created %s monitor for target account %s, property '%s', holding %s, "
                                    + "amount %d, threshold %d, interval %d",
                            holdingType.name(), account.accountName, monitor.property, Long.toUnsignedString(monitor.holdingId),
                            account.amount, account.threshold, account.interval));
                });
                monitors.add(monitor);
                LoggerUtil.logInfo(String.format("%s monitor started for funding account %s, property '%s', holding %s",
                        holdingType.name(), monitor.accountName, monitor.property, Long.toUnsignedString(monitor.holdingId)));
            }
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
        return true;
    }
    private static MonitoredAccount createMonitoredAccount(long accountId, FundMonitoring monitor, String propertyValue) {
        long monitorAmount = monitor.amount;
        long monitorThreshold = monitor.threshold;
        int monitorInterval = monitor.interval;
        if (propertyValue != null && !propertyValue.isEmpty()) {
            try {
                Object parsedValue = JSONValue.parseWithException(propertyValue);
                if (!(parsedValue instanceof JSONObject)) {
                    throw new IllegalArgumentException("Property value is not a JSON object");
                }
                JSONObject jsonValue = (JSONObject) parsedValue;
                monitorAmount = getValue(jsonValue.get("amount"), monitorAmount);
                monitorThreshold = getValue(jsonValue.get("threshold"), monitorThreshold);
                monitorInterval = (int) getValue(jsonValue.get("interval"), monitorInterval);
            } catch (IllegalArgumentException | ParseException exc) {
                String errorMessage = String.format("Account %s, property '%s', value '%s' is not valid",
                        Convert.rsAccount(accountId), monitor.property, propertyValue);
                throw new IllegalArgumentException(errorMessage, exc);
            }
        }
        return new MonitoredAccount(accountId, monitor, monitorAmount, monitorThreshold, monitorInterval);
    }
    private static long getValue(Object jsonValue, long defaultValue) {
        if (jsonValue == null) {
            return defaultValue;
        }
        return Convert.parseLong(jsonValue);
    }
    public static int stopAllMonitors() {
        int stopCount;
        synchronized (monitors) {
            stopCount = monitors.size();
            monitors.clear();
            accounts.clear();
        }
        LoggerUtil.logInfo("All monitors stopped");
        return stopCount;
    }
    public static boolean stopMonitor(HoldingType holdingType, long holdingId, String property, long accountId) {
        FundMonitoring monitor = null;
        boolean wasStopped = false;
        synchronized (monitors) {
            //
            // Deactivate the monitor
            //
            Iterator<FundMonitoring> monitorIt = monitors.iterator();
            while (monitorIt.hasNext()) {
                monitor = monitorIt.next();
                if (monitor.holdingType == holdingType && monitor.property.equals(property) &&
                        (holdingType == HoldingType.EC || monitor.holdingId == holdingId) &&
                        monitor.accountId == accountId) {
                    monitorIt.remove();
                    wasStopped = true;
                    break;
                }
            }
            //
            // Remove monitored accounts (pending fund transactions will still be processed)
            //
            if (wasStopped) {
                Iterator<List<MonitoredAccount>> accountListIt = accounts.values().iterator();
                while (accountListIt.hasNext()) {
                    List<MonitoredAccount> accountList = accountListIt.next();
                    Iterator<MonitoredAccount> accountIt = accountList.iterator();
                    while (accountIt.hasNext()) {
                        MonitoredAccount account = accountIt.next();
                        if (account.monitor == monitor) {
                            accountIt.remove();
                            if (accountList.isEmpty()) {
                                accountListIt.remove();
                            }
                            break;
                        }
                    }
                }
                LoggerUtil.logInfo(String.format("%s monitor stopped for fund account %s, property '%s', holding %d",
                        holdingType.name(), monitor.accountName, monitor.property, monitor.holdingId));
            }
        }
        return wasStopped;
    }
    public static List<FundMonitoring> getMonitors(Filter<FundMonitoring> filter) {
        List<FundMonitoring> result = new ArrayList<>();
        synchronized (monitors) {
            monitors.forEach((monitor) -> {
                if (filter.ok(monitor)) {
                    result.add(monitor);
                }
            });
        }
        return result;
    }
    public static List<FundMonitoring> getAllMonitors() {
        List<FundMonitoring> allMonitors = new ArrayList<>();
        synchronized (monitors) {
            allMonitors.addAll(monitors);
        }
        return allMonitors;
    }
    public static List<MonitoredAccount> getMonitoredAccounts(FundMonitoring monitor) {
        List<MonitoredAccount> monitoredAccounts = new ArrayList<>();
        synchronized (monitors) {
            accounts.values().forEach(monitorList -> monitorList.forEach(account -> {
                if (account.monitor.equals(monitor)) {
                    monitoredAccounts.add(account);
                }
            }));
        }
        return monitoredAccounts;
    }
    private static synchronized void init() {
        if (stopped) {
            throw new RuntimeException("Account monitor processing has been stopped");
        }
        if (started) {
            return;
        }
        try {
            //
            // Create the monitor processing thread
            //
            Thread processingThread = new ProcessEvents();
            processingThread.start();
            //
            // Register our event listeners
            //
            Account.addListener(new AccountEventHandler(), Event.BALANCE);
            Account.addPropertysListener(new AssetEventHandler(), Event.ASSET_BALANCE);
            Account.addCoinListener(new CurrencyEventHandler(), Event.CURRENCY_BALANCE);
            Account.addPropertyListener(new SetPropertyEventHandler(), Event.SET_PROPERTY);
            Account.addPropertyListener(new DeletePropertyEventHandler(), Event.DELETE_PROPERTY);
            EcBlockchainProcessorImpl.getInstance().addECListener(new BlockEventHandler(), EcBlockchainProcessorEvent.BLOCK_PUSHED);
            //
            // All done
            //
            started = true;
            LoggerUtil.logDebug("Account monitor initialization completed");
        } catch (RuntimeException exc) {
            stopped = true;
            LoggerUtil.logError("Account monitor initialization failed", exc);
            throw exc;
        }
    }
    public static void shutdown() {
        if (started && !stopped) {
            stopped = true;
            processSemaphore.release();
        }
    }
    private static void processEcEvent(MonitoredAccount monitoredAccount, Account targetAccount, Account fundingAccount)
            throws EcException {
        FundMonitoring monitor = monitoredAccount.monitor;
        if (targetAccount.getBalanceNQT() < monitoredAccount.threshold) {
            Builder builder = new TransactionImpl.BuilderImpl((byte) 1, monitor.publicKey,
                    monitoredAccount.amount, 0, (short) 1440, (Mortgaged.AbstractMortgaged) Mortgaged.ORDINARY_PAYMENT);
            builder.recipientId(monitoredAccount.accountId)
                    .timestamp(EcBlockchainImpl.getInstance().getLastBlockTimestamp());
            Transaction transaction = builder.build(monitor.secretPhrase);
            if (Math.addExact(monitoredAccount.amount, transaction.getFeeNQT()) > fundingAccount.getUnconfirmedBalanceNQT()) {
                LoggerUtil.logInfo(String.format("Funding account %s has insufficient funds; funding transaction discarded",
                        monitor.accountName));
            } else {
                TransactionProcessorImpl.getInstance().broadcast(transaction);
                monitoredAccount.height = EcBlockchainImpl.getInstance().getHeight();
                LoggerUtil.logDebug(String.format("EC funding transaction %s for %f EC submitted from %s to %s",
                        transaction.getStringId(), (double) monitoredAccount.amount / Constants.ONE_EC,
                        monitor.accountName, monitoredAccount.accountName));
            }
        }
    }
    private static void processAssetEvent(MonitoredAccount monitoredAccount, Account targetAccount, Account fundingAccount)
            throws EcException {
        FundMonitoring monitor = monitoredAccount.monitor;
        Account.AccountPro targetAsset = Account.getAccountProperty(targetAccount.getId(), monitor.holdingId);
        Account.AccountPro fundingAsset = Account.getAccountProperty(fundingAccount.getId(), monitor.holdingId);
        if (fundingAsset == null || fundingAsset.getUnconfirmedQuantityQNT() < monitoredAccount.amount) {
            LoggerUtil.logInfo(
                    String.format("Funding account %s has insufficient quantity for asset %s; funding transaction discarded",
                            monitor.accountName, Long.toUnsignedString(monitor.holdingId)));
        } else if (targetAsset == null || targetAsset.getQuantityQNT() < monitoredAccount.threshold) {
            Mortgaged mortgaged = new Mortgaged.ColoredCoinsAssetTransfer(monitor.holdingId, monitoredAccount.amount);
            Builder builder = new TransactionImpl.BuilderImpl((byte) 1, monitor.publicKey,
                    0, 0, (short) 1440, (Mortgaged.AbstractMortgaged) mortgaged);
            builder.recipientId(monitoredAccount.accountId)
                    .timestamp(EcBlockchainImpl.getInstance().getLastBlockTimestamp());
            Transaction transaction = builder.build(monitor.secretPhrase);
            if (transaction.getFeeNQT() > fundingAccount.getUnconfirmedBalanceNQT()) {
                LoggerUtil.logInfo(String.format("Funding account %s has insufficient funds; funding transaction discarded",
                        monitor.accountName));
            } else {
                TransactionProcessorImpl.getInstance().broadcast(transaction);
                monitoredAccount.height = EcBlockchainImpl.getInstance().getHeight();
                LoggerUtil.logDebug(String.format("ASSET funding transaction %s submitted for %d units from %s to %s",
                        transaction.getStringId(), monitoredAccount.amount,
                        monitor.accountName, monitoredAccount.accountName));
            }
        }
    }
    private static void processCurrencyEvent(MonitoredAccount monitoredAccount, Account targetAccount, Account fundingAccount)
            throws EcException {
        FundMonitoring monitor = monitoredAccount.monitor;
        Account.AccountCoin targetCurrency = Account.getAccountCoin(targetAccount.getId(), monitor.holdingId);
        Account.AccountCoin fundingCurrency = Account.getAccountCoin(fundingAccount.getId(), monitor.holdingId);
        if (fundingCurrency == null || fundingCurrency.getUnconfirmedUnits() < monitoredAccount.amount) {
            LoggerUtil.logInfo(
                    String.format("Funding account %s has insufficient quantity for currency %s; funding transaction discarded",
                            monitor.accountName, Long.toUnsignedString(monitor.holdingId)));
        } else if (targetCurrency == null || targetCurrency.getUnits() < monitoredAccount.threshold) {
            Mortgaged mortgaged = new Mortgaged.MonetarySystemCurrencyTransfer(monitor.holdingId, monitoredAccount.amount);
            Builder builder = new TransactionImpl.BuilderImpl((byte) 1, monitor.publicKey, 0, 0, (short) 1440, (Mortgaged.AbstractMortgaged) mortgaged);
            builder.recipientId(monitoredAccount.accountId)
                    .timestamp(EcBlockchainImpl.getInstance().getLastBlockTimestamp());
            Transaction transaction = builder.build(monitor.secretPhrase);
            if (transaction.getFeeNQT() > fundingAccount.getUnconfirmedBalanceNQT()) {
                LoggerUtil.logInfo(String.format("Funding account %s has insufficient funds; funding transaction discarded",
                        monitor.accountName));
            } else {
                TransactionProcessorImpl.getInstance().broadcast(transaction);
                monitoredAccount.height = EcBlockchainImpl.getInstance().getHeight();
                LoggerUtil.logDebug(String.format("CURRENCY funding transaction %s submitted for %d units from %s to %s",
                        transaction.getStringId(), monitoredAccount.amount,
                        monitor.accountName, monitoredAccount.accountName));
            }
        }
    }
    public HoldingType getHoldingType() {
        return holdingType;
    }
    public long getHoldingId() {
        return holdingId;
    }
    public String getProperty() {
        return property;
    }
    public long getAmount() {
        return amount;
    }
    public long getThreshold() {
        return threshold;
    }
    public int getInterval() {
        return interval;
    }
    public long getAccountId() {
        return accountId;
    }
    public String getAccountName() {
        return accountName;
    }
    @Override
    public int hashCode() {
        return holdingType.hashCode() + (int) holdingId + property.hashCode() + (int) accountId;
    }
    @Override
    public boolean equals(Object obj) {
        boolean isEqual = false;
        if (obj != null && (obj instanceof FundMonitoring)) {
            FundMonitoring monitor = (FundMonitoring) obj;
            if (holdingType == monitor.holdingType && holdingId == monitor.holdingId &&
                    property.equals(monitor.property) && accountId == monitor.accountId) {
                isEqual = true;
            }
        }
        return isEqual;
    }
    private static class ProcessEvents extends Thread {

        /**
         * Process pending updates
         */
        @Override
        public void run() {
            LoggerUtil.logDebug("Account monitor thread started");
            List<MonitoredAccount> suspendedEvents = new ArrayList<>();
            try {
                while (true) {
                    //
                    // Wait for a block to be pushed and then process pending account events
                    //
                    processSemaphore.acquire();
                    if (stopped) {
                        LoggerUtil.logDebug("Account monitor thread stopped");
                        break;
                    }
                    MonitoredAccount monitoredAccount;
                    while ((monitoredAccount = pendingEvents.poll()) != null) {
                        try {
                            Account targetAccount = Account.getAccount(monitoredAccount.accountId);
                            Account fundingAccount = Account.getAccount(monitoredAccount.monitor.accountId);
                            if (EcBlockchainImpl.getInstance().getHeight() - monitoredAccount.height < monitoredAccount.interval) {
                                if (!suspendedEvents.contains(monitoredAccount)) {
                                    suspendedEvents.add(monitoredAccount);
                                }
                            } else if (targetAccount == null) {
                                LoggerUtil.logError(String.format("Monitored account %s no longer exists",
                                        monitoredAccount.accountName));
                            } else if (fundingAccount == null) {
                                LoggerUtil.logError(String.format("Funding account %s no longer exists",
                                        monitoredAccount.monitor.accountName));
                            } else {
                                switch (monitoredAccount.monitor.holdingType) {
                                    case EC:
                                        processEcEvent(monitoredAccount, targetAccount, fundingAccount);
                                        break;
                                    case ASSET:
                                        processAssetEvent(monitoredAccount, targetAccount, fundingAccount);
                                        break;
                                    case CURRENCY:
                                        processCurrencyEvent(monitoredAccount, targetAccount, fundingAccount);
                                        break;
                                }
                            }
                        } catch (Exception exc) {
                            LoggerUtil.logError(String.format("Unable to process %s event for account %s, property '%s', holding %s",
                                    monitoredAccount.monitor.holdingType.name(), monitoredAccount.accountName,
                                    monitoredAccount.monitor.property, Long.toUnsignedString(monitoredAccount.monitor.holdingId)), exc);
                        }
                    }
                    if (!suspendedEvents.isEmpty()) {
                        pendingEvents.addAll(suspendedEvents);
                        suspendedEvents.clear();
                    }
                }
            } catch (InterruptedException exc) {
                LoggerUtil.logDebug("Account monitor thread interrupted");
            } catch (Throwable exc) {
                LoggerUtil.logError("Account monitor thread terminated", exc);
            }
        }
    }
    public static final class MonitoredAccount {

        /**
         * Account identifier
         */
        private final long accountId;

        /**
         * Account name
         */
        private final String accountName;

        /**
         * Associated monitor
         */
        private final FundMonitoring monitor;

        /**
         * Fund amount
         */
        private long amount;

        /**
         * Fund threshold
         */
        private long threshold;

        /**
         * Fund interval
         */
        private int interval;

        /**
         * Last fund height
         */
        private int height;

        /**
         * Create a new monitored account
         *
         * @param accountId Account identifier
         * @param monitor   Account monitor
         * @param amount    Fund amount
         * @param threshold Fund threshold
         * @param interval  Fund interval
         */
        private MonitoredAccount(long accountId, FundMonitoring monitor, long amount, long threshold, int interval) {
            if (amount < Constants.EC_MIN_FUND_AMOUNT) {
                throw new IllegalArgumentException("Minimum fund amount is " + Constants.EC_MIN_FUND_AMOUNT);
            }
            if (threshold < Constants.EC_MIN_FUND_THRESHOLD) {
                throw new IllegalArgumentException("Minimum fund threshold is " + Constants.EC_MIN_FUND_THRESHOLD);
            }
            if (interval < Constants.EC_MIN_FUND_INTERVAL) {
                throw new IllegalArgumentException("Minimum fund interval is " + Constants.EC_MIN_FUND_INTERVAL);
            }
            this.accountId = accountId;
            this.accountName = Convert.rsAccount(accountId);
            this.monitor = monitor;
            this.amount = amount;
            this.threshold = threshold;
            this.interval = interval;
        }

        /**
         * Get the account identifier
         *
         * @return Account identifier
         */
        public long getAccountId() {
            return accountId;
        }

        /**
         * Get the account name (Reed-Solomon encoded account identifier)
         *
         * @return Account name
         */
        public String getAccountName() {
            return accountName;
        }

        /**
         * Get the funding amount
         *
         * @return Funding amount
         */
        public long getAmount() {
            return amount;
        }

        /**
         * Get the funding threshold
         *
         * @return Funding threshold
         */
        public long getThreshold() {
            return threshold;
        }

        /**
         * Get the funding interval
         *
         * @return Funding interval
         */
        public int getInterval() {
            return interval;
        }
    }
    private static final class AccountEventHandler implements Listener<Account> {

        /**
         * Account event notification
         *
         * @param account Account
         */
        @Override
        public void notify(Account account) {
            if (stopped) {
                return;
            }
            long balance = account.getBalanceNQT();
            //
            // Check the EC balance for monitored accounts
            //
            synchronized (monitors) {
                List<MonitoredAccount> accountList = accounts.get(account.getId());
                if (accountList != null) {
                    accountList.forEach((maccount) -> {
                        if (maccount.monitor.holdingType == HoldingType.EC && balance < maccount.threshold &&
                                !pendingEvents.contains(maccount)) {
                            pendingEvents.add(maccount);
                        }
                    });
                }
            }
        }
    }
    private static final class AssetEventHandler implements Listener<Account.AccountPro> {

        /**
         * Property event notification
         *
         * @param asset Account asset
         */
        @Override
        public void notify(Account.AccountPro asset) {
            if (stopped) {
                return;
            }
            long balance = asset.getQuantityQNT();
            long assetId = asset.getAssetId();
            //
            // Check the asset balance for monitored accounts
            //
            synchronized (monitors) {
                List<MonitoredAccount> accountList = accounts.get(asset.getAccountId());
                if (accountList != null) {
                    accountList.forEach((maccount) -> {
                        if (maccount.monitor.holdingType == HoldingType.ASSET &&
                                maccount.monitor.holdingId == assetId &&
                                balance < maccount.threshold &&
                                !pendingEvents.contains(maccount)) {
                            pendingEvents.add(maccount);
                        }
                    });
                }
            }
        }
    }
    private static final class CurrencyEventHandler implements Listener<Account.AccountCoin> {

        /**
         * Coin event notification
         *
         * @param currency Account currency
         */
        @Override
        public void notify(Account.AccountCoin currency) {
            if (stopped) {
                return;
            }
            long balance = currency.getUnits();
            long currencyId = currency.getCurrencyId();
            //
            // Check the currency balance for monitored accounts
            //
            synchronized (monitors) {
                List<MonitoredAccount> accountList = accounts.get(currency.getAccountId());
                if (accountList != null) {
                    accountList.forEach((maccount) -> {
                        if (maccount.monitor.holdingType == HoldingType.CURRENCY &&
                                maccount.monitor.holdingId == currencyId &&
                                balance < maccount.threshold &&
                                !pendingEvents.contains(maccount)) {
                            pendingEvents.add(maccount);
                        }
                    });
                }
            }
        }
    }
    private static final class SetPropertyEventHandler implements Listener<Account.AccountProperty> {

        /**
         * Property event notification
         *
         * @param property Account property
         */
        @Override
        public void notify(Account.AccountProperty property) {
            if (stopped) {
                return;
            }
            long accountId = property.getRecipientId();
            try {
                boolean addMonitoredAccount = true;
                synchronized (monitors) {
                    //
                    // Check if updating an existing monitored account.  In this case, we don't need to create
                    // a new monitored account and just need to update any monitor overrides.
                    //
                    List<MonitoredAccount> accountList = accounts.get(accountId);
                    if (accountList != null) {
                        for (MonitoredAccount account : accountList) {
                            if (account.monitor.property.equals(property.getProperty())) {
                                addMonitoredAccount = false;
                                MonitoredAccount newAccount = createMonitoredAccount(accountId, account.monitor, property.getValue());
                                account.amount = newAccount.amount;
                                account.threshold = newAccount.threshold;
                                account.interval = newAccount.interval;
                                pendingEvents.add(account);
                                LoggerUtil.logDebug(
                                        String.format("Updated %s monitor for account %s, property '%s', holding %s, "
                                                        + "amount %d, threshold %d, interval %d",
                                                account.monitor.holdingType.name(), account.accountName,
                                                property.getProperty(), Long.toUnsignedString(account.monitor.holdingId),
                                                account.amount, account.threshold, account.interval));
                            }
                        }
                    }
                    //
                    // Create a new monitored account if there is an active monitor for this account property
                    //
                    if (addMonitoredAccount) {
                        for (FundMonitoring monitor : monitors) {
                            if (monitor.property.equals(property.getProperty())) {
                                MonitoredAccount account = createMonitoredAccount(accountId, monitor, property.getValue());
                                accountList = accounts.get(accountId);
                                if (accountList == null) {
                                    accountList = new ArrayList<>();
                                    accounts.put(accountId, accountList);
                                }
                                accountList.add(account);
                                pendingEvents.add(account);
                                LoggerUtil.logDebug(
                                        String.format("Created %s monitor for account %s, property '%s', holding %s, "
                                                        + "amount %d, threshold %d, interval %d",
                                                monitor.holdingType.name(), account.accountName,
                                                property.getProperty(), Long.toUnsignedString(monitor.holdingId),
                                                account.amount, account.threshold, account.interval));
                            }
                        }
                    }
                }
            } catch (Exception exc) {
                LoggerUtil.logError("Unable to process SET_PROPERTY event for account " + Convert.rsAccount(accountId), exc);
            }
        }
    }
    private static final class DeletePropertyEventHandler implements Listener<Account.AccountProperty> {

        /**
         * Property event notification
         *
         * @param property Account property
         */
        @Override
        public void notify(Account.AccountProperty property) {
            if (stopped) {
                return;
            }
            long accountId = property.getRecipientId();
            synchronized (monitors) {
                List<MonitoredAccount> accountList = accounts.get(accountId);
                if (accountList != null) {
                    Iterator<MonitoredAccount> it = accountList.iterator();
                    while (it.hasNext()) {
                        MonitoredAccount account = it.next();
                        if (account.monitor.property.equals(property.getProperty())) {
                            it.remove();
                            LoggerUtil.logDebug(
                                    String.format("Deleted %s monitor for account %s, property '%s', holding %s",
                                            account.monitor.holdingType.name(), account.accountName,
                                            property.getProperty(), Long.toUnsignedString(account.monitor.holdingId)));
                        }
                    }
                    if (accountList.isEmpty()) {
                        accounts.remove(accountId);
                    }
                }
            }
        }
    }
    private static final class BlockEventHandler implements Listener<EcBlock> {

        /**
         * EcBlock event notification
         */
        @Override
        public void notify(EcBlock ecBlock) {
            if (!stopped && !pendingEvents.isEmpty()) {
                processSemaphore.release();
            }
        }
    }
}
