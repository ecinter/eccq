package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CoinFounder {

    private static final H2KeyLinkKeyFactory<CoinFounder> CURRENCY_FOUNDER_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<CoinFounder>("currency_id", "account_id") {

        @Override
        public H2Key newKey(CoinFounder coinFounder) {
            return coinFounder.h2Key;
        }

    };

    private static final VersionedEntityH2Table<CoinFounder> CURRENCY_FOUNDER_TABLE = new VersionedEntityH2Table<CoinFounder>("currency_founder", CURRENCY_FOUNDER_DB_KEY_FACTORY) {

        @Override
        protected CoinFounder load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new CoinFounder(rs, h2Key);
        }

        @Override
        protected void save(Connection con, CoinFounder coinFounder) throws SQLException {
            coinFounder.save(con);
        }

        @Override
        public String defaultSort() {
            return " ORDER BY height DESC ";
        }

    };
    private final H2Key h2Key;
    private final long currencyId;
    private final long accountId;
    private long amountPerUnitNQT;
    private CoinFounder(long currencyId, long accountId, long amountPerUnitNQT) {
        this.currencyId = currencyId;
        this.h2Key = CURRENCY_FOUNDER_DB_KEY_FACTORY.newKey(currencyId, accountId);
        this.accountId = accountId;
        this.amountPerUnitNQT = amountPerUnitNQT;
    }

    private CoinFounder(ResultSet rs, H2Key h2Key) throws SQLException {
        this.currencyId = rs.getLong("currency_id");
        this.accountId = rs.getLong("account_id");
        this.h2Key = h2Key;
        this.amountPerUnitNQT = rs.getLong("amount");
    }

    public static void start() {
    }

    static void addOrUpdateFounder(long currencyId, long accountId, long amount) {
        CoinFounder founder = getFounder(currencyId, accountId);
        if (founder == null) {
            founder = new CoinFounder(currencyId, accountId, amount);
        } else {
            founder.amountPerUnitNQT += amount;
        }
        CURRENCY_FOUNDER_TABLE.insert(founder);
    }

    public static CoinFounder getFounder(long currencyId, long accountId) {
        return CURRENCY_FOUNDER_TABLE.get(CURRENCY_FOUNDER_DB_KEY_FACTORY.newKey(currencyId, accountId));
    }

    public static H2Iterator<CoinFounder> getCurrencyFounders(long currencyId, int from, int to) {
        return CURRENCY_FOUNDER_TABLE.getManyBy(new H2ClauseLongClause("currency_id", currencyId), from, to);
    }

    public static H2Iterator<CoinFounder> getFounderCurrencies(long accountId, int from, int to) {
        return CURRENCY_FOUNDER_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
    }

    static void del(long currencyId) {
        List<CoinFounder> founders = new ArrayList<>();
        try (H2Iterator<CoinFounder> currencyFounders = CoinFounder.getCurrencyFounders(currencyId, 0, Integer.MAX_VALUE)) {
            for (CoinFounder founder : currencyFounders) {
                founders.add(founder);
            }
        }
        founders.forEach(CURRENCY_FOUNDER_TABLE::delete);
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO currency_founder (currency_id, account_id, amount, height, latest) "
                + "KEY (currency_id, account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.getCurrencyId());
            pstmt.setLong(++i, this.getAccountId());
            pstmt.setLong(++i, this.getAmountPerUnitNQT());
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getCurrencyId() {
        return currencyId;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getAmountPerUnitNQT() {
        return amountPerUnitNQT;
    }
}
