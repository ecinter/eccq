package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Listener;
import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.kernel.H2.*;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public final class Coin {

    private static final H2KeyLongKeyFactory<Coin> CURRENCY_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Coin>("Id") {

        @Override
        public H2Key newKey(Coin coin) {
            return coin.h2Key == null ? newKey(coin.currencyId) : coin.h2Key;
        }

    };
    private static final VersionedEntityH2Table<Coin> CURRENCY_TABLE = new VersionedEntityH2Table<Coin>("currency", CURRENCY_DB_KEY_FACTORY, "code,name,description") {

        @Override
        protected Coin load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Coin(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Coin coin) throws SQLException {
            coin.saveCoin(con);
        }

        @Override
        public String defaultSort() {
            return " ORDER BY creation_height DESC ";
        }

    };
    private static final H2KeyLongKeyFactory<CoinSupply> CURRENCY_SUPPLY_DB_KEY_FACTORY = new H2KeyLongKeyFactory<CoinSupply>("Id") {

        @Override
        public H2Key newKey(CoinSupply coinSupply) {
            return coinSupply.h2Key;
        }

    };
    private static final VersionedEntityH2Table<CoinSupply> CURRENCY_SUPPLY_TABLE = new VersionedEntityH2Table<CoinSupply>("currency_supply", CURRENCY_SUPPLY_DB_KEY_FACTORY) {

        @Override
        protected CoinSupply load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new CoinSupply(rs, h2Key);
        }

        @Override
        protected void save(Connection con, CoinSupply coinSupply) throws SQLException {
            coinSupply.save(con);
        }

    };
    private static final ListenerManager<Coin, CoinEvent> COIN_EVENT_LISTENER_MANAGER = new ListenerManager<>();

    @PostConstruct
    public static void initPostConstruct() {
        EcBlockchainProcessorImpl.getInstance().addECListener(new CrowdFundingListener(), EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);
    }

    private final long currencyId;
    private final H2Key h2Key;
    private final long accountId;
    private final String name;
    private final String code;
    private final String description;
    private final int type;
    private final long maxSupply;
    private final long reserveSupply;
    private final int creationHeight;
    private final int issuanceHeight;
    private final long minReservePerUnitNQT;
    private final int minDifficulty;
    private final int maxDifficulty;
    private final byte ruleset;
    private final byte algorithm;
    private final byte decimals;
    private final long initialSupply;
    private CoinSupply coinSupply;

    private Coin(Transaction transaction, Mortgaged.MonetarySystemCurrencyIssuance attachment) {
        this.currencyId = transaction.getTransactionId();
        this.h2Key = CURRENCY_DB_KEY_FACTORY.newKey(this.currencyId);
        this.accountId = transaction.getSenderId();
        this.name = attachment.getName();
        this.code = attachment.getCode();
        this.description = attachment.getDescription();
        this.type = attachment.getType();
        this.initialSupply = attachment.getInitialSupply();
        this.reserveSupply = attachment.getReserveSupply();
        this.maxSupply = attachment.getMaxSupply();
        this.creationHeight = EcBlockchainImpl.getInstance().getHeight();
        this.issuanceHeight = attachment.getIssuanceHeight();
        this.minReservePerUnitNQT = attachment.getMinReservePerUnitNQT();
        this.minDifficulty = attachment.getMinDifficulty();
        this.maxDifficulty = attachment.getMaxDifficulty();
        this.ruleset = attachment.getRuleset();
        this.algorithm = attachment.getAlgorithm();
        this.decimals = attachment.getDecimals();
    }

    private Coin(ResultSet rs, H2Key h2Key) throws SQLException {
        this.currencyId = rs.getLong("Id");
        this.h2Key = h2Key;
        this.accountId = rs.getLong("account_id");
        this.name = rs.getString("name");
        this.code = rs.getString("code");
        this.description = rs.getString("description");
        this.type = rs.getInt("type");
        this.initialSupply = rs.getLong("initial_supply");
        this.reserveSupply = rs.getLong("reserve_supply");
        this.maxSupply = rs.getLong("max_supply");
        this.creationHeight = rs.getInt("creation_height");
        this.issuanceHeight = rs.getInt("issuance_height");
        this.minReservePerUnitNQT = rs.getLong("min_reserve_per_unit_nqt");
        this.minDifficulty = rs.getByte("min_difficulty") & 0xFF;
        this.maxDifficulty = rs.getByte("max_difficulty") & 0xFF;
        this.ruleset = rs.getByte("ruleset");
        this.algorithm = rs.getByte("algorithm");
        this.decimals = rs.getByte("decimals");
    }

    public static boolean addCoinListener(Listener<Coin> listener, CoinEvent eventType) {
        return COIN_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removeCoinListener(Listener<Coin> listener, CoinEvent eventType) {
        return COIN_EVENT_LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static H2Iterator<Coin> getAllCurrencies(int from, int to) {
        return CURRENCY_TABLE.getAll(from, to);
    }

    public static int getCount() {
        return CURRENCY_TABLE.getCount();
    }

    public static Coin getCoin(long id) {
        return CURRENCY_TABLE.get(CURRENCY_DB_KEY_FACTORY.newKey(id));
    }

    public static Coin getCoinByName(String name) {
        return CURRENCY_TABLE.getBy(new H2ClauseStringClause("name_lower", name.toLowerCase()));
    }

    public static Coin getCoinByCode(String code) {
        return CURRENCY_TABLE.getBy(new H2ClauseStringClause("code", code.toUpperCase()));
    }

    public static H2Iterator<Coin> getCoinIssuedBy(long accountId, int from, int to) {
        return CURRENCY_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
    }

    public static H2Iterator<Coin> searchCoins(String query, int from, int to) {
        return CURRENCY_TABLE.search(query, H2Clause.EMPTY_CLAUSE, from, to, " ORDER BY ft.score DESC, currency.creation_height DESC ");
    }

    static void addCoin(LedgerEvent event, long eventId, Transaction transaction, Account senderAccount,
                        Mortgaged.MonetarySystemCurrencyIssuance attachment) {
        Coin oldCoin;
        if ((oldCoin = Coin.getCoinByCode(attachment.getCode())) != null) {
            oldCoin.delete(event, eventId, senderAccount);
        }
        if ((oldCoin = Coin.getCoinByCode(attachment.getName())) != null) {
            oldCoin.delete(event, eventId, senderAccount);
        }
        if ((oldCoin = Coin.getCoinByName(attachment.getName())) != null) {
            oldCoin.delete(event, eventId, senderAccount);
        }
        if ((oldCoin = Coin.getCoinByName(attachment.getCode())) != null) {
            oldCoin.delete(event, eventId, senderAccount);
        }
        Coin coin = new Coin(transaction, attachment);
        CURRENCY_TABLE.insert(coin);
        if (coin.is(CoinType.MINTABLE) || coin.is(CoinType.RESERVABLE)) {
            CoinSupply coinSupply = coin.getSupplyData();
            coinSupply.currentSupply = attachment.getInitialSupply();
            CURRENCY_SUPPLY_TABLE.insert(coinSupply);
        }

    }

    public static void start() {
    }

    static void increaseReserve(LedgerEvent event, long eventId, Account account, long currencyId, long amountPerUnitNQT) {
        Coin coin = Coin.getCoin(currencyId);
        account.addToBalanceNQT(event, eventId, -Math.multiplyExact(coin.getReserveSupply(), amountPerUnitNQT));
        CoinSupply coinSupply = coin.getSupplyData();
        coinSupply.currentReservePerUnitNQT += amountPerUnitNQT;
        CURRENCY_SUPPLY_TABLE.insert(coinSupply);
        CoinFounder.addOrUpdateFounder(currencyId, account.getId(), amountPerUnitNQT);
    }

    static void claimReserve(LedgerEvent event, long eventId, Account account, long currencyId, long units) {
        account.addToCurrencyUnits(event, eventId, currencyId, -units);
        Coin coin = Coin.getCoin(currencyId);
        coin.increaseSupply(-units);
        account.addToBalanceAndUnconfirmedBalanceNQT(event, eventId,
                Math.multiplyExact(units, coin.getCurrentReservePerUnitNQT()));
    }

    static void transferCoin(LedgerEvent event, long eventId, Account senderAccount, Account recipientAccount,
                             long currencyId, long units) {
        senderAccount.addToCurrencyUnits(event, eventId, currencyId, -units);
        recipientAccount.addToCurrencyAndUnconfirmedCurrencyUnits(event, eventId, currencyId, units);
    }

    private void saveCoin(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO currency (id, account_id, name, code, "
                + "description, type, initial_supply, reserve_supply, max_supply, creation_height, issuance_height, min_reserve_per_unit_nqt, "
                + "min_difficulty, max_difficulty, ruleset, algorithm, decimals, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.currencyId);
            pstmt.setLong(++i, this.accountId);
            pstmt.setString(++i, this.name);
            pstmt.setString(++i, this.code);
            pstmt.setString(++i, this.description);
            pstmt.setInt(++i, this.type);
            pstmt.setLong(++i, this.initialSupply);
            pstmt.setLong(++i, this.reserveSupply);
            pstmt.setLong(++i, this.maxSupply);
            pstmt.setInt(++i, this.creationHeight);
            pstmt.setInt(++i, this.issuanceHeight);
            pstmt.setLong(++i, this.minReservePerUnitNQT);
            pstmt.setByte(++i, (byte) this.minDifficulty);
            pstmt.setByte(++i, (byte) this.maxDifficulty);
            pstmt.setByte(++i, this.ruleset);
            pstmt.setByte(++i, this.algorithm);
            pstmt.setByte(++i, this.decimals);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return currencyId;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public String getCoinCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getCoinType() {
        return type;
    }

    public long getInitialSupply() {
        return initialSupply;
    }

    public long getCurrentSupply() {
        if (!is(CoinType.RESERVABLE) && !is(CoinType.MINTABLE)) {
            return initialSupply;
        }
        if (getSupplyData() == null) {
            return 0;
        }
        return coinSupply.currentSupply;
    }

    public long getReserveSupply() {
        return reserveSupply;
    }

    public long getMaxSupply() {
        return maxSupply;
    }

    public int getCreationHeight() {
        return creationHeight;
    }

    public int getIssuanceHeight() {
        return issuanceHeight;
    }

    public long getMinReservePerUnitNQT() {
        return minReservePerUnitNQT;
    }

    public int getMinDifficulty() {
        return minDifficulty;
    }

    public int getMaxDifficulty() {
        return maxDifficulty;
    }

    public byte getRuleset() {
        return ruleset;
    }

    public byte getAlgorithm() {
        return algorithm;
    }

    public byte getDecimals() {
        return decimals;
    }

    public long getCurrentReservePerUnitNQT() {
        if (!is(CoinType.RESERVABLE) || getSupplyData() == null) {
            return 0;
        }
        return coinSupply.currentReservePerUnitNQT;
    }

    public boolean isActive() {
        return issuanceHeight <= EcBlockchainImpl.getInstance().getHeight();
    }

    private CoinSupply getSupplyData() {
        if (!is(CoinType.RESERVABLE) && !is(CoinType.MINTABLE)) {
            return null;
        }
        if (coinSupply == null) {
            coinSupply = CURRENCY_SUPPLY_TABLE.get(CURRENCY_DB_KEY_FACTORY.newKey(this));
            if (coinSupply == null) {
                coinSupply = new CoinSupply(this);
            }
        }
        return coinSupply;
    }

    void increaseSupply(long units) {
        getSupplyData();
        coinSupply.currentSupply += units;
        if (coinSupply.currentSupply > maxSupply || coinSupply.currentSupply < 0) {
            coinSupply.currentSupply -= units;
            throw new IllegalArgumentException("Cannot addBadgeData " + units + " to current supply of " + coinSupply.currentSupply);
        }
        CURRENCY_SUPPLY_TABLE.insert(coinSupply);
    }

    public H2Iterator<Account.AccountCoin> getAccounts(int from, int to) {
        return Account.getCoinAccounts(this.currencyId, from, to);
    }

    public H2Iterator<Account.AccountCoin> getAccounts(int height, int from, int to) {
        return Account.getCoinAccounts(this.currencyId, height, from, to);
    }

    public H2Iterator<Conversion> getConverts(int from, int to) {
        return Conversion.getCoinExchanges(this.currencyId, from, to);
    }

    public H2Iterator<CoinTransfer> getTransfers(int from, int to) {
        return CoinTransfer.getCoinTransfers(this.currencyId, from, to);
    }

    public boolean is(CoinType type) {
        return (this.type & type.getCode()) != 0;
    }

    public boolean canBeDeletedBy(long senderAccountId) {
        if (!is(CoinType.NON_SHUFFLEABLE) && Shuffling.getHoldingShufflingCount(currencyId, false) > 0) {
            return false;
        }
        if (!isActive()) {
            return senderAccountId == accountId;
        }
        if (is(CoinType.MINTABLE) && getCurrentSupply() < maxSupply && senderAccountId != accountId) {
            return false;
        }
        try (H2Iterator<Account.AccountCoin> accountCurrencies = Account.getCoinAccounts(this.currencyId, 0, -1)) {
            return !accountCurrencies.hasNext() || accountCurrencies.next().getAccountId() == senderAccountId && !accountCurrencies.hasNext();
        }
    }

    void delete(LedgerEvent event, long eventId, Account senderAccount) {
        if (!canBeDeletedBy(senderAccount.getId())) {
            // shouldn't happen as ownership has already been checked in validate, but as a safety check
            throw new IllegalStateException("Coin " + Long.toUnsignedString(currencyId) + " not entirely owned by " + Long.toUnsignedString(senderAccount.getId()));
        }
        COIN_EVENT_LISTENER_MANAGER.notify(this, CoinEvent.BEFORE_DELETE);
        if (is(CoinType.RESERVABLE)) {
            if (is(CoinType.CLAIMABLE) && isActive()) {
                senderAccount.addToUnconfirmedCurrencyUnits(event, eventId, currencyId,
                        -senderAccount.getCoinUnits(currencyId));
                Coin.claimReserve(event, eventId, senderAccount, currencyId, senderAccount.getCoinUnits(currencyId));
            }
            if (!isActive()) {
                try (H2Iterator<CoinFounder> founders = CoinFounder.getCurrencyFounders(currencyId, 0, Integer.MAX_VALUE)) {
                    for (CoinFounder founder : founders) {
                        Account.getAccount(founder.getAccountId())
                                .addToBalanceAndUnconfirmedBalanceNQT(event, eventId, Math.multiplyExact(reserveSupply,
                                        founder.getAmountPerUnitNQT()));
                    }
                }
            }
            CoinFounder.del(currencyId);
        }
        if (is(CoinType.EXCHANGEABLE)) {
            List<CoinBuyOffer> buyOffers = new ArrayList<>();
            try (H2Iterator<CoinBuyOffer> offers = CoinBuyOffer.getOffers(this, 0, -1)) {
                while (offers.hasNext()) {
                    buyOffers.add(offers.next());
                }
            }
            buyOffers.forEach((offer) -> CoinExchangeOffer.removeOffer(event, offer));
        }
        if (is(CoinType.MINTABLE)) {
            CoinMint.deleteCurrency(this);
        }
        senderAccount.addToUnconfirmedCurrencyUnits(event, eventId, currencyId,
                -senderAccount.getUnconfirmedCoinUnits(currencyId));
        senderAccount.addToCurrencyUnits(event, eventId, currencyId, -senderAccount.getCoinUnits(currencyId));
        CURRENCY_TABLE.delete(this);
    }

    private static final class CoinSupply {

        private final H2Key h2Key;
        private final long currencyId;
        private long currentSupply;
        private long currentReservePerUnitNQT;

        private CoinSupply(Coin coin) {
            this.currencyId = coin.currencyId;
            this.h2Key = CURRENCY_SUPPLY_DB_KEY_FACTORY.newKey(this.currencyId);
        }

        private CoinSupply(ResultSet rs, H2Key h2Key) throws SQLException {
            this.currencyId = rs.getLong("Id");
            this.h2Key = h2Key;
            this.currentSupply = rs.getLong("current_supply");
            this.currentReservePerUnitNQT = rs.getLong("current_reserve_per_unit_nqt");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO currency_supply (id, current_supply, "
                    + "current_reserve_per_unit_nqt, height, latest) "
                    + "KEY (id, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.currencyId);
                pstmt.setLong(++i, this.currentSupply);
                pstmt.setLong(++i, this.currentReservePerUnitNQT);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }
    }

    private static final class CrowdFundingListener implements Listener<EcBlock> {

        @Override
        public void notify(EcBlock ecBlock) {
            if (ecBlock.getHeight() <= Constants.EC_MONETARY_SYSTEM_BLOCK) {
                return;
            }

            try (H2Iterator<Coin> issuedCurrencies = CURRENCY_TABLE.getManyBy(new H2ClauseIntClause("issuance_height", ecBlock.getHeight()), 0, -1)) {
                for (Coin coin : issuedCurrencies) {
                    if (coin.getCurrentReservePerUnitNQT() < coin.getMinReservePerUnitNQT()) {
                        COIN_EVENT_LISTENER_MANAGER.notify(coin, CoinEvent.BEFORE_UNDO_CROWDFUNDING);
                        undoCrowdFunding(coin);
                    } else {
                        COIN_EVENT_LISTENER_MANAGER.notify(coin, CoinEvent.BEFORE_DISTRIBUTE_CROWDFUNDING);
                        distributeCurrency(coin);
                    }
                }
            }
        }

        private void undoCrowdFunding(Coin coin) {
            try (H2Iterator<CoinFounder> founders = CoinFounder.getCurrencyFounders(coin.getId(), 0, Integer.MAX_VALUE)) {
                for (CoinFounder founder : founders) {
                    Account.getAccount(founder.getAccountId())
                            .addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_UNDO_CROWDFUNDING, coin.getId(),
                                    Math.multiplyExact(coin.getReserveSupply(),
                                            founder.getAmountPerUnitNQT()));
                }
            }
            Account.getAccount(coin.getAccountId())
                    .addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_UNDO_CROWDFUNDING, coin.getId(),
                            coin.getId(), -coin.getInitialSupply());
            CURRENCY_TABLE.delete(coin);
            CoinFounder.del(coin.getId());
        }

        private void distributeCurrency(Coin coin) {
            long totalAmountPerUnit = 0;
            final long remainingSupply = coin.getReserveSupply() - coin.getInitialSupply();
            List<CoinFounder> coinFounders = new ArrayList<>();
            try (H2Iterator<CoinFounder> founders = CoinFounder.getCurrencyFounders(coin.getId(), 0, Integer.MAX_VALUE)) {
                for (CoinFounder founder : founders) {
                    totalAmountPerUnit += founder.getAmountPerUnitNQT();
                    coinFounders.add(founder);
                }
            }
            CoinSupply coinSupply = coin.getSupplyData();
            for (CoinFounder founder : coinFounders) {
                long units = Math.multiplyExact(remainingSupply, founder.getAmountPerUnitNQT()) / totalAmountPerUnit;
                coinSupply.currentSupply += units;
                Account.getAccount(founder.getAccountId())
                        .addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_DISTRIBUTION, coin.getId(),
                                coin.getId(), units);
            }
            Account issuerAccount = Account.getAccount(coin.getAccountId());
            issuerAccount.addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent.CURRENCY_DISTRIBUTION, coin.getId(),
                    coin.getId(), coin.getReserveSupply() - coin.getCurrentSupply());
            if (!coin.is(CoinType.CLAIMABLE)) {
                issuerAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.CURRENCY_DISTRIBUTION, coin.getId(),
                        Math.multiplyExact(totalAmountPerUnit, coin.getReserveSupply()));
            }
            coinSupply.currentSupply = coin.getReserveSupply();
            CURRENCY_SUPPLY_TABLE.insert(coinSupply);
        }
    }
}
