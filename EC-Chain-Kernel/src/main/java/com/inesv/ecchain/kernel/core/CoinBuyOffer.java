package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CoinBuyOffer extends CoinExchangeOffer {

    private static final H2KeyLongKeyFactory<CoinBuyOffer> BUY_OFFER_DB_KEY_FACTORY = new H2KeyLongKeyFactory<CoinBuyOffer>("Id") {

        @Override
        public H2Key newKey(CoinBuyOffer offer) {
            return offer.h2Key;
        }

    };

    private static final VersionedEntityH2Table<CoinBuyOffer> BUY_OFFER_TABLE = new VersionedEntityH2Table<CoinBuyOffer>("buy_offer", BUY_OFFER_DB_KEY_FACTORY) {

        @Override
        protected CoinBuyOffer load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new CoinBuyOffer(rs, h2Key);
        }

        @Override
        protected void save(Connection con, CoinBuyOffer buy) throws SQLException {
            buy.save(con, table);
        }

    };

    private final H2Key h2Key;

    private CoinBuyOffer(Transaction transaction, Mortgaged.MonetarySystemPublishExchangeOffer attachment) {
        super(transaction.getTransactionId(), attachment.getCurrencyId(), transaction.getSenderId(), attachment.getBuyRateNQT(),
                attachment.getTotalBuyLimit(), attachment.getInitialBuySupply(), attachment.getExpirationHeight(), transaction.getTransactionHeight(),
                transaction.getTransactionIndex());
        this.h2Key = BUY_OFFER_DB_KEY_FACTORY.newKey(id);
    }

    private CoinBuyOffer(ResultSet rs, H2Key h2Key) throws SQLException {
        super(rs);
        this.h2Key = h2Key;
    }

    public static int getCount() {
        return BUY_OFFER_TABLE.getCount();
    }

    public static CoinBuyOffer getOffer(long offerId) {
        return BUY_OFFER_TABLE.get(BUY_OFFER_DB_KEY_FACTORY.newKey(offerId));
    }

    public static H2Iterator<CoinBuyOffer> getAll(int from, int to) {
        return BUY_OFFER_TABLE.getAll(from, to);
    }

    public static H2Iterator<CoinBuyOffer> getOffers(Coin coin, int from, int to) {
        return getCurrencyOffers(coin.getId(), false, from, to);
    }

    public static H2Iterator<CoinBuyOffer> getCurrencyOffers(long currencyId, boolean availableOnly, int from, int to) {
        H2Clause h2Clause = new H2ClauseLongClause("currency_id", currencyId);
        if (availableOnly) {
            h2Clause = h2Clause.and(AVAILABLE_ONLY_H_2_CLAUSE);
        }
        return BUY_OFFER_TABLE.getManyBy(h2Clause, from, to, " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public static H2Iterator<CoinBuyOffer> getAccountOffers(long accountId, boolean availableOnly, int from, int to) {
        H2Clause h2Clause = new H2ClauseLongClause("account_id", accountId);
        if (availableOnly) {
            h2Clause = h2Clause.and(AVAILABLE_ONLY_H_2_CLAUSE);
        }
        return BUY_OFFER_TABLE.getManyBy(h2Clause, from, to, " ORDER BY rate DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
    }

    public static CoinBuyOffer getOffer(Coin coin, Account account) {
        return getOffer(coin.getId(), account.getId());
    }

    public static CoinBuyOffer getOffer(final long currencyId, final long accountId) {
        return BUY_OFFER_TABLE.getBy(new H2ClauseLongClause("currency_id", currencyId).and(new H2ClauseLongClause("account_id", accountId)));
    }

    public static H2Iterator<CoinBuyOffer> getOffers(H2Clause h2Clause, int from, int to) {
        return BUY_OFFER_TABLE.getManyBy(h2Clause, from, to);
    }

    public static H2Iterator<CoinBuyOffer> getOffers(H2Clause h2Clause, int from, int to, String sort) {
        return BUY_OFFER_TABLE.getManyBy(h2Clause, from, to, sort);
    }

    static void addOffer(Transaction transaction, Mortgaged.MonetarySystemPublishExchangeOffer attachment) {
        BUY_OFFER_TABLE.insert(new CoinBuyOffer(transaction, attachment));
    }

    static void remove(CoinBuyOffer buyOffer) {
        BUY_OFFER_TABLE.delete(buyOffer);
    }

    public static void start() {
    }

    @Override
    public CoinSellOffer getCounterOffer() {
        return CoinSellOffer.getOffer(id);
    }

    long increaseSupply(long delta) {
        long excess = super.increaseSupply(delta);
        BUY_OFFER_TABLE.insert(this);
        return excess;
    }

    void decreaseLimitAndSupply(long delta) {
        super.decreaseLimitAndSupply(delta);
        BUY_OFFER_TABLE.insert(this);
    }

}
