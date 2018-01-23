package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CoinSellOffer extends CoinExchangeOffer {

    private static final H2KeyLongKeyFactory<CoinSellOffer> SELL_OFFER_DB_KEY_FACTORY = new H2KeyLongKeyFactory<CoinSellOffer>("Id") {

        @Override
        public H2Key newKey(CoinSellOffer sell) {
            return sell.h2Key;
        }

    };

    private static final VersionedEntityH2Table<CoinSellOffer> SELL_OFFER_TABLE = new VersionedEntityH2Table<CoinSellOffer>("sell_offer", SELL_OFFER_DB_KEY_FACTORY) {

        @Override
        protected CoinSellOffer load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new CoinSellOffer(rs, h2Key);
        }

        @Override
        protected void save(Connection con, CoinSellOffer sell) throws SQLException {
            sell.save(con, table);
        }

    };

    private final H2Key h2Key;

    private CoinSellOffer(Transaction transaction, Mortgaged.MonetarySystemPublishExchangeOffer attachment) {
        super(transaction.getTransactionId(), attachment.getCurrencyId(), transaction.getSenderId(), attachment.getSellRateNQT(),
                attachment.getTotalSellLimit(), attachment.getInitialSellSupply(), attachment.getExpirationHeight(), transaction.getTransactionHeight(),
                transaction.getTransactionIndex());
        this.h2Key = SELL_OFFER_DB_KEY_FACTORY.newKey(id);
    }

    private CoinSellOffer(ResultSet rs, H2Key h2Key) throws SQLException {
        super(rs);
        this.h2Key = h2Key;
    }

    public static int getCount() {
        return SELL_OFFER_TABLE.getCount();
    }

    public static CoinSellOffer getOffer(long id) {
        return SELL_OFFER_TABLE.get(SELL_OFFER_DB_KEY_FACTORY.newKey(id));
    }

    public static H2Iterator<CoinSellOffer> getAll(int from, int to) {
        return SELL_OFFER_TABLE.getAll(from, to);
    }

    public static H2Iterator<CoinSellOffer> getCurrencyOffers(long currencyId, boolean availableOnly, int from, int to) {
        H2Clause h2Clause = new H2ClauseLongClause("currency_id", currencyId);
        if (availableOnly) {
            h2Clause = h2Clause.and(AVAILABLE_ONLY_H_2_CLAUSE);
        }
        return SELL_OFFER_TABLE.getManyBy(h2Clause, from, to, " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public static H2Iterator<CoinSellOffer> getAccountOffers(long accountId, boolean availableOnly, int from, int to) {
        H2Clause h2Clause = new H2ClauseLongClause("account_id", accountId);
        if (availableOnly) {
            h2Clause = h2Clause.and(AVAILABLE_ONLY_H_2_CLAUSE);
        }
        return SELL_OFFER_TABLE.getManyBy(h2Clause, from, to, " ORDER BY rate ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public static CoinSellOffer getOffer(Coin coin, Account account) {
        return getOffer(coin.getId(), account.getId());
    }

    public static CoinSellOffer getOffer(final long currencyId, final long accountId) {
        return SELL_OFFER_TABLE.getBy(new H2ClauseLongClause("currency_id", currencyId).and(new H2ClauseLongClause("account_id", accountId)));
    }

    public static H2Iterator<CoinSellOffer> getOffers(H2Clause h2Clause, int from, int to, String sort) {
        return SELL_OFFER_TABLE.getManyBy(h2Clause, from, to, sort);
    }

    static void addOffer(Transaction transaction, Mortgaged.MonetarySystemPublishExchangeOffer attachment) {
        SELL_OFFER_TABLE.insert(new CoinSellOffer(transaction, attachment));
    }

    static void del(CoinSellOffer sellOffer) {
        SELL_OFFER_TABLE.delete(sellOffer);
    }

    public static void start() {
    }

    @Override
    public CoinBuyOffer getCounterOffer() {
        return CoinBuyOffer.getOffer(id);
    }

    long increaseSupply(long delta) {
        long excess = super.increaseSupply(delta);
        SELL_OFFER_TABLE.insert(this);
        return excess;
    }

    void decreaseLimitAndSupply(long delta) {
        super.decreaseLimitAndSupply(delta);
        SELL_OFFER_TABLE.insert(this);
    }
}
