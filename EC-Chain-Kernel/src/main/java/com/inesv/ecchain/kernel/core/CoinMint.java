package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.Listener;
import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CoinMint {

    private static final H2KeyLinkKeyFactory<CoinMint> CURRENCY_MINT_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<CoinMint>("currency_id", "account_id") {

        @Override
        public H2Key newKey(CoinMint coinMint) {
            return coinMint.h2Key;
        }

    };
    private static final VersionedEntityH2Table<CoinMint> CURRENCY_MINT_TABLE = new VersionedEntityH2Table<CoinMint>("currency_mint", CURRENCY_MINT_DB_KEY_FACTORY) {

        @Override
        protected CoinMint load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new CoinMint(rs, h2Key);
        }

        @Override
        protected void save(Connection con, CoinMint coinMint) throws SQLException {
            coinMint.save(con);
        }

    };
    private static final ListenerManager<Mint, CoinMintEvent> MINT_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private final H2Key h2Key;
    private final long currencyId;
    private final long accountId;
    private long counter;


    private CoinMint(long currencyId, long accountId, long counter) {
        this.currencyId = currencyId;
        this.accountId = accountId;
        this.h2Key = CURRENCY_MINT_DB_KEY_FACTORY.newKey(this.currencyId, this.accountId);
        this.counter = counter;
    }

    private CoinMint(ResultSet rs, H2Key h2Key) throws SQLException {
        this.currencyId = rs.getLong("currency_id");
        this.accountId = rs.getLong("account_id");
        this.h2Key = h2Key;
        this.counter = rs.getLong("counter");
    }

    public static boolean addCoinMintListener(Listener<Mint> listener, CoinMintEvent eventType) {
        return MINT_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static void start() {
    }

    static void mintCurrency(LedgerEvent event, long eventId, final Account account,
                             final Mortgaged.MonetarySystemCurrencyMinting attachment) {
        CoinMint coinMint = CURRENCY_MINT_TABLE.get(CURRENCY_MINT_DB_KEY_FACTORY.newKey(attachment.getCurrencyId(), account.getId()));
        if (coinMint != null && attachment.getCounter() <= coinMint.getCounter()) {
            return;
        }
        Coin coin = Coin.getCoin(attachment.getCurrencyId());
        if (CoinMinting.meetsTarget(account.getId(), coin, attachment)) {
            if (coinMint == null) {
                coinMint = new CoinMint(attachment.getCurrencyId(), account.getId(), attachment.getCounter());
            } else {
                coinMint.counter = attachment.getCounter();
            }
            CURRENCY_MINT_TABLE.insert(coinMint);
            long units = Math.min(attachment.getUnits(), coin.getMaxSupply() - coin.getCurrentSupply());
            account.addToCurrencyAndUnconfirmedCurrencyUnits(event, eventId, coin.getId(), units);
            coin.increaseSupply(units);
            MINT_EVENT_LISTENER_MANAGER.notify(new Mint(account.getId(), coin.getId(), units), CoinMintEvent.CURRENCY_MINT);
        } else {
            LoggerUtil.logInfo("Coin mint hash no longer meets target " + attachment.getJSONObject().toJSONString());
        }
    }

    public static long getCounter(long currencyId, long accountId) {
        CoinMint coinMint = CURRENCY_MINT_TABLE.get(CURRENCY_MINT_DB_KEY_FACTORY.newKey(currencyId, accountId));
        if (coinMint != null) {
            return coinMint.getCounter();
        } else {
            return 0;
        }
    }

    static void deleteCurrency(Coin coin) {
        List<CoinMint> coinMints = new ArrayList<>();
        try (H2Iterator<CoinMint> mints = CURRENCY_MINT_TABLE.getManyBy(new H2ClauseLongClause("currency_id", coin.getId()), 0, -1)) {
            while (mints.hasNext()) {
                coinMints.add(mints.next());
            }
        }
        coinMints.forEach(CURRENCY_MINT_TABLE::delete);
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO currency_mint (currency_id, account_id, counter, height, latest) "
                + "KEY (currency_id, account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.currencyId);
            pstmt.setLong(++i, this.accountId);
            pstmt.setLong(++i, this.counter);
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

    public long getCounter() {
        return counter;
    }

}
