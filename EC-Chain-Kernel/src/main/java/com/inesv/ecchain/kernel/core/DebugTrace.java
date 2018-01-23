package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.kernel.H2.H2Iterator;

import javax.annotation.PostConstruct;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

public final class DebugTrace {

    static final String QUOTE = PropertiesUtil.getKeyForString("ec.debugTraceQuote", "\"");
    static final String SEPARATOR = PropertiesUtil.getKeyForString("ec.debugTraceSeparator", "\t");
    static final boolean LOG_UNCONFIRMED = PropertiesUtil.getKeyForBoolean("ec.debugLogUnconfirmed");

    private static final String[] columns = {"height", "event", "account", "asset", "currency", "balance", "unconfirmed balance",
            "asset balance", "unconfirmed asset balance", "currency balance", "unconfirmed currency balance",
            "transaction amount", "transaction fee", "generation fee", "effective balance", "dividend",
            "order", "order price", "order quantity", "order cost",
            "offer", "buy rate", "sell rate", "buy units", "sell units", "buy cost", "sell cost",
            "trade price", "trade quantity", "trade cost",
            "exchange rate", "exchange quantity", "exchange cost", "currency cost",
            "crowdfunding", "claim", "mint",
            "asset quantity", "currency units", "transaction", "lessee", "lessor guaranteed balance",
            "purchase", "purchase price", "purchase quantity", "purchase cost", "discount", "refund",
            "shuffling",
            "sender", "recipient", "block", "timestamp"};
    private static final Map<String, String> headers = new HashMap<>();
    @PostConstruct
    public static void initPostConstruct() {
        for (String entry : columns) {
            headers.put(entry, entry);
        }
    }

    private final Set<Long> accountIds;
    private final String logName;
    private PrintWriter log;

    private DebugTrace(Set<Long> accountIds, String logName) {
        this.accountIds = accountIds;
        this.logName = logName;
        resetLog();
    }

    public static void start() {
        List<String> accountIdStrings = PropertiesUtil.getStringListProperty("ec.debugTraceAccounts");
        String logName = PropertiesUtil.getKeyForString("ec.debugTraceLog", null);
        if (accountIdStrings.isEmpty() || logName == null) {
            return;
        }
        Set<Long> accountIds = new HashSet<>();
        for (String accountId : accountIdStrings) {
            if ("*".equals(accountId)) {
                accountIds.clear();
                break;
            }
            accountIds.add(Convert.parseAccountId(accountId));
        }
        final DebugTrace debugTrace = addDebugTrace(accountIds, logName);
        EcBlockchainProcessorImpl.getInstance().addECListener(block -> debugTrace.resetLog(), EcBlockchainProcessorEvent.RESCAN_BEGIN);
        LoggerUtil.logDebug("Debug tracing of " + (accountIdStrings.contains("*") ? "ALL"
                : String.valueOf(accountIds.size())) + " accounts enabled");
    }

    public static DebugTrace addDebugTrace(Set<Long> accountIds, String logName) {
        final DebugTrace debugTrace = new DebugTrace(accountIds, logName);
        Trade.addTradeListener(debugTrace::trace, TradeEvent.TRADE);
        Conversion.addConversionListener(debugTrace::trace, ConversionEvent.EXCHANGE);
        Coin.addCoinListener(debugTrace::crowdfunding, CoinEvent.BEFORE_DISTRIBUTE_CROWDFUNDING);
        Coin.addCoinListener(debugTrace::undoCrowdfunding, CoinEvent.BEFORE_UNDO_CROWDFUNDING);
        Coin.addCoinListener(debugTrace::delete, CoinEvent.BEFORE_DELETE);
        CoinMint.addCoinMintListener(debugTrace::currencyMint, CoinMintEvent.CURRENCY_MINT);
        Account.addListener(account -> debugTrace.trace(account, false), Event.BALANCE);
        if (LOG_UNCONFIRMED) {
            Account.addListener(account -> debugTrace.trace(account, true), Event.UNCONFIRMED_BALANCE);
        }
        Account.addPropertysListener(accountAsset -> debugTrace.trace(accountAsset, false), Event.ASSET_BALANCE);
        if (LOG_UNCONFIRMED) {
            Account.addPropertysListener(accountAsset -> debugTrace.trace(accountAsset, true), Event.UNCONFIRMED_ASSET_BALANCE);
        }
        Account.addCoinListener(accountCurrency -> debugTrace.trace(accountCurrency, false), Event.CURRENCY_BALANCE);
        if (LOG_UNCONFIRMED) {
            Account.addCoinListener(accountCurrency -> debugTrace.trace(accountCurrency, true), Event.UNCONFIRMED_CURRENCY_BALANCE);
        }
        Account.addLeaseListener(accountLease -> debugTrace.trace(accountLease, true), Event.LEASE_STARTED);
        Account.addLeaseListener(accountLease -> debugTrace.trace(accountLease, false), Event.LEASE_ENDED);
        EcBlockchainProcessorImpl.getInstance().addECListener(debugTrace::traceBeforeAccept, EcBlockchainProcessorEvent.BEFORE_BLOCK_ACCEPT);
        EcBlockchainProcessorImpl.getInstance().addECListener(debugTrace::trace, EcBlockchainProcessorEvent.BEFORE_BLOCK_APPLY);
        TransactionProcessorImpl.getInstance().addECListener(transactions -> debugTrace.traceRelease(transactions.get(0)), TransactionProcessorEvent.RELEASE_PHASED_TRANSACTION);
        Shuffling.addShufflingListener(debugTrace::traceShufflingDistribute, ShufflingEvent.SHUFFLING_DONE);
        Shuffling.addShufflingListener(debugTrace::traceShufflingCancel, ShufflingEvent.SHUFFLING_CANCELLED);
        return debugTrace;
    }

    void resetLog() {
        if (log != null) {
            log.close();
        }
        try {
            log = new PrintWriter((new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logName)))), true);
        } catch (IOException e) {
            LoggerUtil.logError("Debug tracing to " + logName + " not possible", e);
            throw new RuntimeException(e);
        }
        this.log(headers);
    }

    private boolean include(long accountId) {
        return accountId != 0 && (accountIds.isEmpty() || accountIds.contains(accountId));
    }

    private void trace(Trade trade) {
        long askAccountId = Order.Ask.getAskOrder(trade.getOrderId()).getAccountId();
        long bidAccountId = Order.Bid.getBidOrder(trade.getBidOrderId()).getAccountId();
        if (include(askAccountId)) {
            log(getValues(askAccountId, trade, true));
        }
        if (include(bidAccountId)) {
            log(getValues(bidAccountId, trade, false));
        }
    }

    private void trace(Conversion conversion) {
        long sellerAccountId = conversion.getSellerId();
        long buyerAccountId = conversion.getBuyerId();
        if (include(sellerAccountId)) {
            log(getValues(sellerAccountId, conversion, true));
        }
        if (include(buyerAccountId)) {
            log(getValues(buyerAccountId, conversion, false));
        }
    }

    private void trace(Account account, boolean unconfirmed) {
        if (include(account.getId())) {
            log(getValues(account.getId(), unconfirmed));
        }
    }

    private void trace(Account.AccountPro accountPro, boolean unconfirmed) {
        if (!include(accountPro.getAccountId())) {
            return;
        }
        log(getValues(accountPro.getAccountId(), accountPro, unconfirmed));
    }

    private void trace(Account.AccountCoin accountCoin, boolean unconfirmed) {
        if (!include(accountCoin.getAccountId())) {
            return;
        }
        log(getValues(accountCoin.getAccountId(), accountCoin, unconfirmed));
    }

    private void trace(Account.AccountLease accountLease, boolean start) {
        if (!include(accountLease.getCurrentLesseeId()) && !include(accountLease.getLessorId())) {
            return;
        }
        log(getValues(accountLease.getLessorId(), accountLease, start));
    }

    private void traceBeforeAccept(EcBlock ecBlock) {
        long generatorId = ecBlock.getFoundryId();
        if (include(generatorId)) {
            log(getValues(generatorId, ecBlock));
        }
        for (long accountId : accountIds) {
            Account account = Account.getAccount(accountId);
            if (account != null) {
                try (H2Iterator<Account> lessors = account.getLessors()) {
                    while (lessors.hasNext()) {
                        log(lessorGuaranteedBalance(lessors.next(), accountId));
                    }
                }
            }
        }
    }

    private void trace(EcBlock ecBlock) {
        for (Transaction transaction : ecBlock.getTransactions()) {
            long senderId = transaction.getSenderId();
            if (((TransactionImpl) transaction).attachmentIsPhased()) {
                if (include(senderId)) {
                    log(getValues(senderId, transaction, false, true, false));
                }
                continue;
            }
            if (include(senderId)) {
                log(getValues(senderId, transaction, false, true, true));
                log(getValues(senderId, transaction, transaction.getAttachment(), false));
            }
            long recipientId = transaction.getRecipientId();
            if (transaction.getAmountNQT() > 0 && recipientId == 0) {
                recipientId = Genesis.EC_CREATOR_ID;
            }
            if (include(recipientId)) {
                log(getValues(recipientId, transaction, true, true, true));
                log(getValues(recipientId, transaction, transaction.getAttachment(), true));
            }
        }
    }

    private void traceRelease(Transaction transaction) {
        long senderId = transaction.getSenderId();
        if (include(senderId)) {
            log(getValues(senderId, transaction, false, false, true));
            log(getValues(senderId, transaction, transaction.getAttachment(), false));
        }
        long recipientId = transaction.getRecipientId();
        if (include(recipientId)) {
            log(getValues(recipientId, transaction, true, false, true));
            log(getValues(recipientId, transaction, transaction.getAttachment(), true));
        }
    }

    private void traceShufflingDistribute(Shuffling shuffling) {
        ShufflingParticipant.getParticipants(shuffling.getId()).forEach(shufflingParticipant -> {
            if (include(shufflingParticipant.getAccountId())) {
                log(getValues(shufflingParticipant.getAccountId(), shuffling, false));
            }
        });
        for (byte[] recipientPublicKey : shuffling.getRecipientPublicKeys()) {
            long recipientId = Account.getId(recipientPublicKey);
            if (include(recipientId)) {
                log(getValues(recipientId, shuffling, true));
            }
        }
    }

    private void traceShufflingCancel(Shuffling shuffling) {
        long blamedAccountId = shuffling.getAssigneeAccountId();
        if (blamedAccountId != 0 && include(blamedAccountId)) {
            Map<String, String> map = getValues(blamedAccountId, false);
            map.put("transaction fee", String.valueOf(-Constants.EC_SHUFFLING_DEPOSIT_NQT));
            map.put("event", "shuffling blame");
            log(map);
            long fee = Constants.EC_SHUFFLING_DEPOSIT_NQT / 4;
            int height = EcBlockchainImpl.getInstance().getHeight();
            for (int i = 0; i < 3; i++) {
                long generatorId = EcBlockH2.findBlockAtHeight(height - i - 1).getFoundryId();
                if (include(generatorId)) {
                    Map<String, String> generatorMap = getValues(generatorId, false);
                    generatorMap.put("generation fee", String.valueOf(fee));
                    generatorMap.put("event", "shuffling blame");
                    log(generatorMap);
                }
            }
            fee = Constants.EC_SHUFFLING_DEPOSIT_NQT - 3 * fee;
            long generatorId = EcBlockchainImpl.getInstance().getLastECBlock().getFoundryId();
            if (include(generatorId)) {
                Map<String, String> generatorMap = getValues(generatorId, false);
                generatorMap.put("generation fee", String.valueOf(fee));
                generatorMap.put("event", "shuffling blame");
                log(generatorMap);
            }
        }
    }

    private Map<String, String> lessorGuaranteedBalance(Account account, long lesseeId) {
        Map<String, String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(account.getId()));
        map.put("lessor guaranteed balance", String.valueOf(account.getGuaranteedBalanceNQT()));
        map.put("lessee", Long.toUnsignedString(lesseeId));
        map.put("timestamp", String.valueOf(EcBlockchainImpl.getInstance().getLastECBlock().getTimestamp()));
        map.put("height", String.valueOf(EcBlockchainImpl.getInstance().getHeight()));
        map.put("event", "lessor guaranteed balance");
        return map;
    }

    private void crowdfunding(Coin coin) {
        long totalAmountPerUnit = 0;
        long foundersTotal = 0;
        final long remainingSupply = coin.getReserveSupply() - coin.getInitialSupply();
        List<CoinFounder> coinFounders = new ArrayList<>();
        try (H2Iterator<CoinFounder> founders = CoinFounder.getCurrencyFounders(coin.getId(), 0, Integer.MAX_VALUE)) {
            for (CoinFounder founder : founders) {
                totalAmountPerUnit += founder.getAmountPerUnitNQT();
                coinFounders.add(founder);
            }
        }
        for (CoinFounder founder : coinFounders) {
            long units = Math.multiplyExact(remainingSupply, founder.getAmountPerUnitNQT()) / totalAmountPerUnit;
            Map<String, String> founderMap = getValues(founder.getAccountId(), false);
            founderMap.put("coin", Long.toUnsignedString(coin.getId()));
            founderMap.put("coin units", String.valueOf(units));
            founderMap.put("event", "distribution");
            log(founderMap);
            foundersTotal += units;
        }
        Map<String, String> map = getValues(coin.getAccountId(), false);
        map.put("coin", Long.toUnsignedString(coin.getId()));
        map.put("crowdfunding", String.valueOf(coin.getReserveSupply()));
        map.put("coin units", String.valueOf(remainingSupply - foundersTotal));
        if (!coin.is(CoinType.CLAIMABLE)) {
            map.put("coin cost", String.valueOf(Math.multiplyExact(coin.getReserveSupply(), coin.getCurrentReservePerUnitNQT())));
        }
        map.put("event", "crowdfunding");
        log(map);
    }

    private void undoCrowdfunding(Coin coin) {
        try (H2Iterator<CoinFounder> founders = CoinFounder.getCurrencyFounders(coin.getId(), 0, Integer.MAX_VALUE)) {
            for (CoinFounder founder : founders) {
                Map<String, String> founderMap = getValues(founder.getAccountId(), false);
                founderMap.put("coin", Long.toUnsignedString(coin.getId()));
                founderMap.put("coin cost", String.valueOf(Math.multiplyExact(coin.getReserveSupply(), founder.getAmountPerUnitNQT())));
                founderMap.put("event", "undo distribution");
                log(founderMap);
            }
        }
        Map<String, String> map = getValues(coin.getAccountId(), false);
        map.put("coin", Long.toUnsignedString(coin.getId()));
        map.put("coin units", String.valueOf(-coin.getInitialSupply()));
        map.put("event", "undo crowdfunding");
        log(map);
    }

    private void delete(Coin coin) {
        long accountId = 0;
        long units = 0;
        if (!coin.isActive()) {
            accountId = coin.getAccountId();
            units = coin.getCurrentSupply();
        } else {
            try (H2Iterator<Account.AccountCoin> accountCurrencies = Account.getCoinAccounts(coin.getId(), 0, -1)) {
                if (accountCurrencies.hasNext()) {
                    Account.AccountCoin accountCoin = accountCurrencies.next();
                    accountId = accountCoin.getAccountId();
                    units = accountCoin.getUnits();
                } else {
                    return;
                }
            }
        }
        Map<String, String> map = getValues(accountId, false);
        map.put("coin", Long.toUnsignedString(coin.getId()));
        if (coin.is(CoinType.RESERVABLE)) {
            if (coin.is(CoinType.CLAIMABLE) && coin.isActive()) {
                map.put("coin cost", String.valueOf(Math.multiplyExact(units, coin.getCurrentReservePerUnitNQT())));
            }
            if (!coin.isActive()) {
                try (H2Iterator<CoinFounder> founders = CoinFounder.getCurrencyFounders(coin.getId(), 0, Integer.MAX_VALUE)) {
                    for (CoinFounder founder : founders) {
                        Map<String, String> founderMap = getValues(founder.getAccountId(), false);
                        founderMap.put("coin", Long.toUnsignedString(coin.getId()));
                        founderMap.put("coin cost", String.valueOf(Math.multiplyExact(coin.getReserveSupply(), founder.getAmountPerUnitNQT())));
                        founderMap.put("event", "undo distribution");
                        log(founderMap);
                    }
                }
            }
        }
        map.put("coin units", String.valueOf(-units));
        map.put("event", "coin delete");
        log(map);
    }

    private void currencyMint(Mint mint) {
        if (!include(mint.accountId)) {
            return;
        }
        Map<String, String> map = getValues(mint.accountId, false);
        map.put("currency", Long.toUnsignedString(mint.currencyId));
        map.put("currency units", String.valueOf(mint.units));
        map.put("event", "currency mint");
        log(map);
    }

    private Map<String, String> getValues(long accountId, boolean unconfirmed) {
        Map<String, String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(accountId));
        Account account = Account.getAccount(accountId);
        map.put("balance", String.valueOf(account != null ? account.getBalanceNQT() : 0));
        map.put("unconfirmed balance", String.valueOf(account != null ? account.getUnconfirmedBalanceNQT() : 0));
        map.put("timestamp", String.valueOf(EcBlockchainImpl.getInstance().getLastECBlock().getTimestamp()));
        map.put("height", String.valueOf(EcBlockchainImpl.getInstance().getHeight()));
        map.put("event", unconfirmed ? "unconfirmed balance" : "balance");
        return map;
    }

    private Map<String, String> getValues(long accountId, Trade trade, boolean isAsk) {
        Map<String, String> map = getValues(accountId, false);
        map.put("asset", Long.toUnsignedString(trade.getAssetId()));
        map.put("trade quantity", String.valueOf(isAsk ? -trade.getQuantityQNT() : trade.getQuantityQNT()));
        map.put("trade price", String.valueOf(trade.getPriceNQT()));
        long tradeCost = Math.multiplyExact(trade.getQuantityQNT(), trade.getPriceNQT());
        map.put("trade cost", String.valueOf((isAsk ? tradeCost : -tradeCost)));
        map.put("event", "trade");
        return map;
    }

    private Map<String, String> getValues(long accountId, Conversion conversion, boolean isSell) {
        Map<String, String> map = getValues(accountId, false);
        map.put("currency", Long.toUnsignedString(conversion.getCurrencyId()));
        map.put("conversion quantity", String.valueOf(isSell ? -conversion.getUnits() : conversion.getUnits()));
        map.put("conversion rate", String.valueOf(conversion.getRate()));
        long exchangeCost = Math.multiplyExact(conversion.getUnits(), conversion.getRate());
        map.put("conversion cost", String.valueOf((isSell ? exchangeCost : -exchangeCost)));
        map.put("event", "conversion");
        return map;
    }

    private Map<String, String> getValues(long accountId, Shuffling shuffling, boolean isRecipient) {
        Map<String, String> map = getValues(accountId, false);
        map.put("shuffling", Long.toUnsignedString(shuffling.getId()));
        String amount = String.valueOf(isRecipient ? shuffling.getAmount() : -shuffling.getAmount());
        String deposit = String.valueOf(isRecipient ? Constants.EC_SHUFFLING_DEPOSIT_NQT : -Constants.EC_SHUFFLING_DEPOSIT_NQT);
        if (shuffling.getHoldingType() == HoldingType.EC) {
            map.put("transaction amount", amount);
        } else if (shuffling.getHoldingType() == HoldingType.ASSET) {
            map.put("asset quantity", amount);
            map.put("asset", Long.toUnsignedString(shuffling.getHoldingId()));
            map.put("transaction amount", deposit);
        } else if (shuffling.getHoldingType() == HoldingType.CURRENCY) {
            map.put("currency units", amount);
            map.put("currency", Long.toUnsignedString(shuffling.getHoldingId()));
            map.put("transaction amount", deposit);
        } else {
            throw new RuntimeException("Unsupported holding type " + shuffling.getHoldingType());
        }
        map.put("event", "shuffling distribute");
        return map;
    }

    private Map<String, String> getValues(long accountId, Transaction transaction, boolean isRecipient, boolean logFee, boolean logAmount) {
        long amount = transaction.getAmountNQT();
        long fee = transaction.getFeeNQT();
        if (isRecipient) {
            fee = 0; // fee doesn't affect recipient account
        } else {
            // for sender the amounts are subtracted
            amount = -amount;
            fee = -fee;
        }
        if (fee == 0 && amount == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> map = getValues(accountId, false);
        if (logAmount) {
            map.put("transaction amount", String.valueOf(amount));
        }
        if (logFee) {
            map.put("transaction fee", String.valueOf(fee));
        }
        map.put("transaction", transaction.getStringId());
        if (isRecipient) {
            map.put("sender", Long.toUnsignedString(transaction.getSenderId()));
        } else {
            map.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
        }
        map.put("event", "transaction");
        return map;
    }

    private Map<String, String> getValues(long accountId, EcBlock ecBlock) {
        long fee = ecBlock.getTotalFeeNQT();
        if (fee == 0) {
            return Collections.emptyMap();
        }
        long totalBackFees = 0;
        if (ecBlock.getHeight() > Constants.EC_SHUFFLING_BLOCK) {
            long[] backFees = new long[3];
            for (Transaction transaction : ecBlock.getTransactions()) {
                long[] fees = ((TransactionImpl) transaction).getBackFees();
                for (int i = 0; i < fees.length; i++) {
                    backFees[i] += fees[i];
                }
            }
            for (int i = 0; i < backFees.length; i++) {
                if (backFees[i] == 0) {
                    break;
                }
                totalBackFees += backFees[i];
                long previousGeneratorId = EcBlockH2.findBlockAtHeight(ecBlock.getHeight() - i - 1).getFoundryId();
                if (include(previousGeneratorId)) {
                    Map<String, String> map = getValues(previousGeneratorId, false);
                    map.put("effective balance", String.valueOf(Account.getAccount(previousGeneratorId).getEffectiveBalanceEC()));
                    map.put("generation fee", String.valueOf(backFees[i]));
                    map.put("ecBlock", ecBlock.getStringECId());
                    map.put("event", "ecBlock");
                    map.put("timestamp", String.valueOf(ecBlock.getTimestamp()));
                    map.put("height", String.valueOf(ecBlock.getHeight()));
                    log(map);
                }
            }
        }
        Map<String, String> map = getValues(accountId, false);
        map.put("effective balance", String.valueOf(Account.getAccount(accountId).getEffectiveBalanceEC()));
        map.put("generation fee", String.valueOf(fee - totalBackFees));
        map.put("ecBlock", ecBlock.getStringECId());
        map.put("event", "ecBlock");
        map.put("timestamp", String.valueOf(ecBlock.getTimestamp()));
        map.put("height", String.valueOf(ecBlock.getHeight()));
        return map;
    }

    private Map<String, String> getValues(long accountId, Account.AccountPro accountPro, boolean unconfirmed) {
        Map<String, String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(accountId));
        map.put("asset", Long.toUnsignedString(accountPro.getAssetId()));
        if (unconfirmed) {
            map.put("unconfirmed asset balance", String.valueOf(accountPro.getUnconfirmedQuantityQNT()));
        } else {
            map.put("asset balance", String.valueOf(accountPro.getQuantityQNT()));
        }
        map.put("timestamp", String.valueOf(EcBlockchainImpl.getInstance().getLastECBlock().getTimestamp()));
        map.put("height", String.valueOf(EcBlockchainImpl.getInstance().getHeight()));
        map.put("event", "asset balance");
        return map;
    }

    private Map<String, String> getValues(long accountId, Account.AccountCoin accountCoin, boolean unconfirmed) {
        Map<String, String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(accountId));
        map.put("currency", Long.toUnsignedString(accountCoin.getCurrencyId()));
        if (unconfirmed) {
            map.put("unconfirmed currency balance", String.valueOf(accountCoin.getUnconfirmedUnits()));
        } else {
            map.put("currency balance", String.valueOf(accountCoin.getUnits()));
        }
        map.put("timestamp", String.valueOf(EcBlockchainImpl.getInstance().getLastECBlock().getTimestamp()));
        map.put("height", String.valueOf(EcBlockchainImpl.getInstance().getHeight()));
        map.put("event", "currency balance");
        return map;
    }

    private Map<String, String> getValues(long accountId, Account.AccountLease accountLease, boolean start) {
        Map<String, String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(accountId));
        map.put("event", start ? "lease begin" : "lease end");
        map.put("timestamp", String.valueOf(EcBlockchainImpl.getInstance().getLastECBlock().getTimestamp()));
        map.put("height", String.valueOf(EcBlockchainImpl.getInstance().getHeight()));
        map.put("lessee", Long.toUnsignedString(accountLease.getCurrentLesseeId()));
        return map;
    }

    private Map<String, String> getValues(long accountId, Transaction transaction, Mortgaged mortgaged, boolean isRecipient) {
        Map<String, String> map = getValues(accountId, false);
        if (mortgaged instanceof Mortgaged.ColoredCoinsOrderPlacement) {
            if (isRecipient) {
                return Collections.emptyMap();
            }
            Mortgaged.ColoredCoinsOrderPlacement orderPlacement = (Mortgaged.ColoredCoinsOrderPlacement) mortgaged;
            boolean isAsk = orderPlacement instanceof Mortgaged.ColoredCoinsAskOrderPlacement;
            map.put("asset", Long.toUnsignedString(orderPlacement.getAssetId()));
            map.put("order", transaction.getStringId());
            map.put("order price", String.valueOf(orderPlacement.getPriceNQT()));
            long quantity = orderPlacement.getQuantityQNT();
            if (isAsk) {
                quantity = -quantity;
            }
            map.put("order quantity", String.valueOf(quantity));
            BigInteger orderCost = BigInteger.valueOf(orderPlacement.getPriceNQT()).multiply(BigInteger.valueOf(orderPlacement.getQuantityQNT()));
            if (!isAsk) {
                orderCost = orderCost.negate();
            }
            map.put("order cost", orderCost.toString());
            String event = (isAsk ? "ask" : "bid") + " order";
            map.put("event", event);
        } else if (mortgaged instanceof Mortgaged.ColoredCoinsAssetIssuance) {
            if (isRecipient) {
                return Collections.emptyMap();
            }
            Mortgaged.ColoredCoinsAssetIssuance assetIssuance = (Mortgaged.ColoredCoinsAssetIssuance) mortgaged;
            map.put("asset", transaction.getStringId());
            map.put("asset quantity", String.valueOf(assetIssuance.getQuantityQNT()));
            map.put("event", "asset issuance");
        } else if (mortgaged instanceof Mortgaged.ColoredCoinsAssetTransfer) {
            Mortgaged.ColoredCoinsAssetTransfer assetTransfer = (Mortgaged.ColoredCoinsAssetTransfer) mortgaged;
            map.put("asset", Long.toUnsignedString(assetTransfer.getAssetId()));
            long quantity = assetTransfer.getQuantityQNT();
            if (!isRecipient) {
                quantity = -quantity;
            }
            map.put("asset quantity", String.valueOf(quantity));
            map.put("event", "asset transfer");
        } else if (mortgaged instanceof Mortgaged.ColoredCoinsAssetDelete) {
            if (isRecipient) {
                return Collections.emptyMap();
            }
            Mortgaged.ColoredCoinsAssetDelete assetDelete = (Mortgaged.ColoredCoinsAssetDelete) mortgaged;
            map.put("asset", Long.toUnsignedString(assetDelete.getAssetId()));
            long quantity = assetDelete.getQuantityQNT();
            map.put("asset quantity", String.valueOf(-quantity));
            map.put("event", "asset delete");
        } else if (mortgaged instanceof Mortgaged.ColoredCoinsOrderCancellation) {
            Mortgaged.ColoredCoinsOrderCancellation orderCancellation = (Mortgaged.ColoredCoinsOrderCancellation) mortgaged;
            map.put("order", Long.toUnsignedString(orderCancellation.getOrderId()));
            map.put("event", "order cancel");
        } else if (mortgaged instanceof Mortgaged.DigitalGoodsPurchase) {
            Mortgaged.DigitalGoodsPurchase purchase = (Mortgaged.DigitalGoodsPurchase) transaction.getAttachment();
            if (isRecipient) {
                map = getValues(ElectronicProductStore.Goods.getGoods(purchase.getGoodsId()).getSellerId(), false);
            }
            map.put("event", "purchase");
            map.put("purchase", transaction.getStringId());
        } else if (mortgaged instanceof Mortgaged.DigitalGoodsDelivery) {
            Mortgaged.DigitalGoodsDelivery delivery = (Mortgaged.DigitalGoodsDelivery) transaction.getAttachment();
            ElectronicProductStore.Purchase purchase = ElectronicProductStore.Purchase.getPurchase(delivery.getPurchaseId());
            if (isRecipient) {
                map = getValues(purchase.getBuyerId(), false);
            }
            map.put("event", "delivery");
            map.put("purchase", Long.toUnsignedString(delivery.getPurchaseId()));
            long discount = delivery.getDiscountNQT();
            map.put("purchase price", String.valueOf(purchase.getPriceNQT()));
            map.put("purchase quantity", String.valueOf(purchase.getQuantity()));
            long cost = Math.multiplyExact(purchase.getPriceNQT(), (long) purchase.getQuantity());
            if (isRecipient) {
                cost = -cost;
            }
            map.put("purchase cost", String.valueOf(cost));
            if (!isRecipient) {
                discount = -discount;
            }
            map.put("discount", String.valueOf(discount));
        } else if (mortgaged instanceof Mortgaged.DigitalGoodsRefund) {
            Mortgaged.DigitalGoodsRefund refund = (Mortgaged.DigitalGoodsRefund) transaction.getAttachment();
            if (isRecipient) {
                map = getValues(ElectronicProductStore.Purchase.getPurchase(refund.getPurchaseId()).getBuyerId(), false);
            }
            map.put("event", "refund");
            map.put("purchase", Long.toUnsignedString(refund.getPurchaseId()));
            long refundNQT = refund.getRefundNQT();
            if (!isRecipient) {
                refundNQT = -refundNQT;
            }
            map.put("refund", String.valueOf(refundNQT));
        } else if (mortgaged == Mortgaged.ARBITRARY_MESSAGE) {
            map = new HashMap<>();
            map.put("account", Long.toUnsignedString(accountId));
            map.put("timestamp", String.valueOf(EcBlockchainImpl.getInstance().getLastECBlock().getTimestamp()));
            map.put("height", String.valueOf(EcBlockchainImpl.getInstance().getHeight()));
            map.put("event", mortgaged == Mortgaged.ARBITRARY_MESSAGE ? "message" : "encrypted message");
            if (isRecipient) {
                map.put("sender", Long.toUnsignedString(transaction.getSenderId()));
            } else {
                map.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
            }
        } else if (mortgaged instanceof Mortgaged.MonetarySystemPublishExchangeOffer) {
            Mortgaged.MonetarySystemPublishExchangeOffer publishOffer = (Mortgaged.MonetarySystemPublishExchangeOffer) mortgaged;
            map.put("currency", Long.toUnsignedString(publishOffer.getCurrencyId()));
            map.put("offer", transaction.getStringId());
            map.put("buy rate", String.valueOf(publishOffer.getBuyRateNQT()));
            map.put("sell rate", String.valueOf(publishOffer.getSellRateNQT()));
            long buyUnits = publishOffer.getInitialBuySupply();
            map.put("buy units", String.valueOf(buyUnits));
            long sellUnits = publishOffer.getInitialSellSupply();
            map.put("sell units", String.valueOf(sellUnits));
            BigInteger buyCost = BigInteger.valueOf(publishOffer.getBuyRateNQT()).multiply(BigInteger.valueOf(buyUnits));
            map.put("buy cost", buyCost.toString());
            BigInteger sellCost = BigInteger.valueOf(publishOffer.getSellRateNQT()).multiply(BigInteger.valueOf(sellUnits));
            map.put("sell cost", sellCost.toString());
            map.put("event", "offer");
        } else if (mortgaged instanceof Mortgaged.MonetarySystemCurrencyIssuance) {
            Mortgaged.MonetarySystemCurrencyIssuance currencyIssuance = (Mortgaged.MonetarySystemCurrencyIssuance) mortgaged;
            map.put("currency", transaction.getStringId());
            map.put("currency units", String.valueOf(currencyIssuance.getInitialSupply()));
            map.put("event", "currency issuance");
        } else if (mortgaged instanceof Mortgaged.MonetarySystemCurrencyTransfer) {
            Mortgaged.MonetarySystemCurrencyTransfer currencyTransfer = (Mortgaged.MonetarySystemCurrencyTransfer) mortgaged;
            map.put("currency", Long.toUnsignedString(currencyTransfer.getCurrencyId()));
            long units = currencyTransfer.getUnits();
            if (!isRecipient) {
                units = -units;
            }
            map.put("currency units", String.valueOf(units));
            map.put("event", "currency transfer");
        } else if (mortgaged instanceof Mortgaged.MonetarySystemReserveClaim) {
            Mortgaged.MonetarySystemReserveClaim claim = (Mortgaged.MonetarySystemReserveClaim) mortgaged;
            map.put("coin", Long.toUnsignedString(claim.getCurrencyId()));
            Coin coin = Coin.getCoin(claim.getCurrencyId());
            map.put("coin units", String.valueOf(-claim.getUnits()));
            map.put("coin cost", String.valueOf(Math.multiplyExact(claim.getUnits(), coin.getCurrentReservePerUnitNQT())));
            map.put("event", "coin claim");
        } else if (mortgaged instanceof Mortgaged.MonetarySystemReserveIncrease) {
            Mortgaged.MonetarySystemReserveIncrease reserveIncrease = (Mortgaged.MonetarySystemReserveIncrease) mortgaged;
            map.put("coin", Long.toUnsignedString(reserveIncrease.getCurrencyId()));
            Coin coin = Coin.getCoin(reserveIncrease.getCurrencyId());
            map.put("coin cost", String.valueOf(-Math.multiplyExact(reserveIncrease.getAmountPerUnitNQT(), coin.getReserveSupply())));
            map.put("event", "coin reserve");
        } else if (mortgaged instanceof Mortgaged.ColoredCoinsDividendPayment) {
            Mortgaged.ColoredCoinsDividendPayment dividendPayment = (Mortgaged.ColoredCoinsDividendPayment) mortgaged;
            long totalDividend = 0;
            String assetId = Long.toUnsignedString(dividendPayment.getAssetId());
            try (H2Iterator<Account.AccountPro> iterator = Account.getPropertyAccounts(dividendPayment.getAssetId(), dividendPayment.getHeight(), 0, -1)) {
                while (iterator.hasNext()) {
                    Account.AccountPro accountPro = iterator.next();
                    if (accountPro.getAccountId() != accountId && accountPro.getQuantityQNT() != 0) {
                        long dividend = Math.multiplyExact(accountPro.getQuantityQNT(), dividendPayment.getAmountNQTPerQNT());
                        Map recipient = getValues(accountPro.getAccountId(), false);
                        recipient.put("dividend", String.valueOf(dividend));
                        recipient.put("asset", assetId);
                        recipient.put("event", "dividend");
                        totalDividend += dividend;
                        log(recipient);
                    }
                }
            }
            map.put("dividend", String.valueOf(-totalDividend));
            map.put("asset", assetId);
            map.put("event", "dividend");
        } else {
            return Collections.emptyMap();
        }
        return map;
    }

    private void log(Map<String, String> map) {
        if (map.isEmpty()) {
            return;
        }
        StringBuilder buf = new StringBuilder();
        for (String column : columns) {
            if (!LOG_UNCONFIRMED && column.startsWith("unconfirmed")) {
                continue;
            }
            String value = map.get(column);
            if (value != null) {
                buf.append(QUOTE).append(value).append(QUOTE);
            }
            buf.append(SEPARATOR);
        }
        log.println(buf.toString());
    }

}
