package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class CoinExchangeOffer {

    static final H2Clause AVAILABLE_ONLY_H_2_CLAUSE = new H2ClauseLongClause("unit_limit", H2ClauseOp.NE, 0)
            .and(new H2ClauseLongClause("supply", H2ClauseOp.NE, 0));

    static {

        EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
            if (block.getHeight() <= Constants.EC_MONETARY_SYSTEM_BLOCK) {
                return;
            }
            List<CoinBuyOffer> expired = new ArrayList<>();
            try (H2Iterator<CoinBuyOffer> offers = CoinBuyOffer.getOffers(new H2ClauseIntClause("expiration_height", block.getHeight()), 0, -1)) {
                for (CoinBuyOffer offer : offers) {
                    expired.add(offer);
                }
            }
            expired.forEach((offer) -> CoinExchangeOffer.removeOffer(LedgerEvent.CURRENCY_OFFER_EXPIRED, offer));
        }, EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);

    }

    final long id;
    private final long currencyId;
    private final long accountId;
    private final long rateNQT;
    private final int expirationHeight;
    private final int creationHeight;
    private final short transactionIndex;
    private final int transactionHeight;
    private long limit; // limit on the total sum of units for this offer across transactions
    private long supply; // total units supply for the offer


    CoinExchangeOffer(long id, long currencyId, long accountId, long rateNQT, long limit, long supply,
                      int expirationHeight, int transactionHeight, short transactionIndex) {
        this.id = id;
        this.currencyId = currencyId;
        this.accountId = accountId;
        this.rateNQT = rateNQT;
        this.limit = limit;
        this.supply = supply;
        this.expirationHeight = expirationHeight;
        this.creationHeight = EcBlockchainImpl.getInstance().getHeight();
        this.transactionIndex = transactionIndex;
        this.transactionHeight = transactionHeight;
    }
    CoinExchangeOffer(ResultSet rs) throws SQLException {
        this.id = rs.getLong("Id");
        this.currencyId = rs.getLong("currency_id");
        this.accountId = rs.getLong("account_id");
        this.rateNQT = rs.getLong("rate");
        this.limit = rs.getLong("unit_limit");
        this.supply = rs.getLong("supply");
        this.expirationHeight = rs.getInt("expiration_height");
        this.creationHeight = rs.getInt("creation_height");
        this.transactionIndex = rs.getShort("transaction_index");
        this.transactionHeight = rs.getInt("transaction_height");
    }

    static void publishOffer(Transaction transaction, Mortgaged.MonetarySystemPublishExchangeOffer attachment) {
        CoinBuyOffer previousOffer = CoinBuyOffer.getOffer(attachment.getCurrencyId(), transaction.getSenderId());
        if (previousOffer != null) {
            CoinExchangeOffer.removeOffer(LedgerEvent.CURRENCY_OFFER_REPLACED, previousOffer);
        }
        CoinBuyOffer.addOffer(transaction, attachment);
        CoinSellOffer.addOffer(transaction, attachment);
    }

    private static AvailableOffers calculateTotal(List<CoinExchangeOffer> offers, final long units) {
        long totalAmountNQT = 0;
        long remainingUnits = units;
        long rateNQT = 0;
        for (CoinExchangeOffer offer : offers) {
            if (remainingUnits == 0) {
                break;
            }
            rateNQT = offer.getRateNQT();
            long curUnits = Math.min(Math.min(remainingUnits, offer.getSupply()), offer.getLimit());
            long curAmountNQT = Math.multiplyExact(curUnits, offer.getRateNQT());
            totalAmountNQT = Math.addExact(totalAmountNQT, curAmountNQT);
            remainingUnits = Math.subtractExact(remainingUnits, curUnits);
        }
        return new AvailableOffers(rateNQT, Math.subtractExact(units, remainingUnits), totalAmountNQT);
    }

    public static AvailableOffers getAvailableToSell(final long currencyId, final long units) {
        return calculateTotal(getAvailableBuyOffers(currencyId, 0L), units);
    }

    private static List<CoinExchangeOffer> getAvailableBuyOffers(long currencyId, long minRateNQT) {
        List<CoinExchangeOffer> coinExchangeOffers = new ArrayList<>();
        H2Clause h2Clause = new H2ClauseLongClause("currency_id", currencyId).and(AVAILABLE_ONLY_H_2_CLAUSE);
        if (minRateNQT > 0) {
            h2Clause = h2Clause.and(new H2ClauseLongClause("rate", H2ClauseOp.GTE, minRateNQT));
        }
        try (H2Iterator<CoinBuyOffer> offers = CoinBuyOffer.getOffers(h2Clause, 0, -1,
                " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ")) {
            for (CoinBuyOffer offer : offers) {
                coinExchangeOffers.add(offer);
            }
        }
        return coinExchangeOffers;
    }

    static void exchangeCurrencyForEC(Transaction transaction, Account account, final long currencyId, final long rateNQT, final long units) {
        List<CoinExchangeOffer> currencyBuyOffers = getAvailableBuyOffers(currencyId, rateNQT);

        long totalAmountNQT = 0;
        long remainingUnits = units;
        for (CoinExchangeOffer offer : currencyBuyOffers) {
            if (remainingUnits == 0) {
                break;
            }
            long curUnits = Math.min(Math.min(remainingUnits, offer.getSupply()), offer.getLimit());
            long curAmountNQT = Math.multiplyExact(curUnits, offer.getRateNQT());

            totalAmountNQT = Math.addExact(totalAmountNQT, curAmountNQT);
            remainingUnits = Math.subtractExact(remainingUnits, curUnits);

            offer.decreaseLimitAndSupply(curUnits);
            long excess = offer.getCounterOffer().increaseSupply(curUnits);

            Account counterAccount = Account.getAccount(offer.getAccountId());
            counterAccount.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), -curAmountNQT);
            counterAccount.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), currencyId, curUnits);
            counterAccount.addToUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), currencyId, excess);
            Conversion.addConvert(transaction, currencyId, offer, account.getId(), offer.getAccountId(), curUnits);
        }
        long transactionId = transaction.getTransactionId();
        account.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, totalAmountNQT);
        account.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, transactionId, currencyId, -(units - remainingUnits));
        account.addToUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, transactionId, currencyId, remainingUnits);
    }

    public static AvailableOffers getAvailableToBuy(final long currencyId, final long units) {
        return calculateTotal(getAvailableSellOffers(currencyId, 0L), units);
    }

    private static List<CoinExchangeOffer> getAvailableSellOffers(long currencyId, long maxRateNQT) {
        List<CoinExchangeOffer> currencySellOffers = new ArrayList<>();
        H2Clause h2Clause = new H2ClauseLongClause("currency_id", currencyId).and(AVAILABLE_ONLY_H_2_CLAUSE);
        if (maxRateNQT > 0) {
            h2Clause = h2Clause.and(new H2ClauseLongClause("rate", H2ClauseOp.LTE, maxRateNQT));
        }
        try (H2Iterator<CoinSellOffer> offers = CoinSellOffer.getOffers(h2Clause, 0, -1,
                " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ")) {
            for (CoinSellOffer offer : offers) {
                currencySellOffers.add(offer);
            }
        }
        return currencySellOffers;
    }

    static void exchangeECForCurrency(Transaction transaction, Account account, final long currencyId, final long rateNQT, final long units) {
        List<CoinExchangeOffer> currencySellOffers = getAvailableSellOffers(currencyId, rateNQT);

        if (EcBlockchainImpl.getInstance().getHeight() < Constants.EC_SHUFFLING_BLOCK) {
            long totalUnits = 0;
            long totalAmountNQT = Math.multiplyExact(units, rateNQT);
            long remainingAmountNQT = totalAmountNQT;

            for (CoinExchangeOffer offer : currencySellOffers) {
                if (remainingAmountNQT == 0) {
                    break;
                }
                long curUnits = Math.min(Math.min(remainingAmountNQT / offer.getRateNQT(), offer.getSupply()), offer.getLimit());
                if (curUnits == 0) {
                    continue;
                }
                long curAmountNQT = Math.multiplyExact(curUnits, offer.getRateNQT());

                totalUnits = Math.addExact(totalUnits, curUnits);
                remainingAmountNQT = Math.subtractExact(remainingAmountNQT, curAmountNQT);

                offer.decreaseLimitAndSupply(curUnits);
                long excess = offer.getCounterOffer().increaseSupply(curUnits);

                Account counterAccount = Account.getAccount(offer.getAccountId());
                counterAccount.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), curAmountNQT);
                counterAccount.addToUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(),
                        Math.addExact(
                                Math.multiplyExact(curUnits - excess, offer.getRateNQT() - offer.getCounterOffer().getRateNQT()),
                                Math.multiplyExact(excess, offer.getRateNQT())
                        )
                );
                counterAccount.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), currencyId, -curUnits);
                Conversion.addConvert(transaction, currencyId, offer, offer.getAccountId(), account.getId(), curUnits);
            }
            long transactionId = transaction.getTransactionId();
            account.addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, transactionId,
                    currencyId, totalUnits);
            account.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, -(totalAmountNQT - remainingAmountNQT));
            account.addToUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, remainingAmountNQT);
        } else {
            long totalAmountNQT = 0;
            long remainingUnits = units;

            for (CoinExchangeOffer offer : currencySellOffers) {
                if (remainingUnits == 0) {
                    break;
                }
                long curUnits = Math.min(Math.min(remainingUnits, offer.getSupply()), offer.getLimit());
                long curAmountNQT = Math.multiplyExact(curUnits, offer.getRateNQT());

                totalAmountNQT = Math.addExact(totalAmountNQT, curAmountNQT);
                remainingUnits = Math.subtractExact(remainingUnits, curUnits);

                offer.decreaseLimitAndSupply(curUnits);
                long excess = offer.getCounterOffer().increaseSupply(curUnits);

                Account counterAccount = Account.getAccount(offer.getAccountId());
                counterAccount.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), curAmountNQT);
                counterAccount.addToUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(),
                        Math.addExact(
                                Math.multiplyExact(curUnits - excess, offer.getRateNQT() - offer.getCounterOffer().getRateNQT()),
                                Math.multiplyExact(excess, offer.getRateNQT())
                        )
                );
                counterAccount.addToCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, offer.getId(), currencyId, -curUnits);
                Conversion.addConvert(transaction, currencyId, offer, offer.getAccountId(), account.getId(), curUnits);
            }
            long transactionId = transaction.getTransactionId();
            account.addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_EXCHANGE, transactionId,
                    currencyId, Math.subtractExact(units, remainingUnits));
            account.addToBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, -totalAmountNQT);
            account.addToUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_EXCHANGE, transactionId, Math.multiplyExact(units, rateNQT) - totalAmountNQT);
        }
    }

    static void removeOffer(LedgerEvent event, CoinBuyOffer buyOffer) {
        CoinSellOffer sellOffer = buyOffer.getCounterOffer();

        CoinBuyOffer.remove(buyOffer);
        CoinSellOffer.del(sellOffer);

        Account account = Account.getAccount(buyOffer.getAccountId());
        account.addToUnconfirmedBalanceNQT(event, buyOffer.getId(), Math.multiplyExact(buyOffer.getSupply(), buyOffer.getRateNQT()));
        account.addToUnconfirmedCurrencyUnits(event, buyOffer.getId(), buyOffer.getCurrencyId(), sellOffer.getSupply());
    }

    void save(Connection con, String table) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO " + table + " (id, currency_id, account_id, "
                + "rate, unit_limit, supply, expiration_height, creation_height, transaction_index, transaction_height, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.currencyId);
            pstmt.setLong(++i, this.accountId);
            pstmt.setLong(++i, this.rateNQT);
            pstmt.setLong(++i, this.limit);
            pstmt.setLong(++i, this.supply);
            pstmt.setInt(++i, this.expirationHeight);
            pstmt.setInt(++i, this.creationHeight);
            pstmt.setShort(++i, this.transactionIndex);
            pstmt.setInt(++i, this.transactionHeight);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getCurrencyId() {
        return currencyId;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getRateNQT() {
        return rateNQT;
    }

    public long getLimit() {
        return limit;
    }

    public long getSupply() {
        return supply;
    }

    public int getExpirationHeight() {
        return expirationHeight;
    }

    public int getHeight() {
        return creationHeight;
    }

    public abstract CoinExchangeOffer getCounterOffer();

    long increaseSupply(long delta) {
        long excess = Math.max(Math.addExact(supply, Math.subtractExact(delta, limit)), 0);
        supply += delta - excess;
        return excess;
    }

    void decreaseLimitAndSupply(long delta) {
        limit -= delta;
        supply -= delta;
    }

    public static final class AvailableOffers {

        private final long rateNQT;
        private final long units;
        private final long amountNQT;

        private AvailableOffers(long rateNQT, long units, long amountNQT) {
            this.rateNQT = rateNQT;
            this.units = units;
            this.amountNQT = amountNQT;
        }

        public long getRateNQT() {
            return rateNQT;
        }

        public long getUnits() {
            return units;
        }

        public long getAmountNQT() {
            return amountNQT;
        }

    }
}
