package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.H2.*;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings({"UnusedDeclaration", "SuspiciousNameCombination"})
public final class Account {

    private static final H2KeyLongKeyFactory<Account> ACCOUNT_LONG_KEY_FACTORY = new H2KeyLongKeyFactory<Account>("Id") {

        @Override
        public H2Key newKey(Account account) {
            return account.h2Key == null ? newKey(account.id) : account.h2Key;
        }

        @Override
        public Account newEntity(H2Key h2Key) {
            return new Account(((H2KeyLongKey) h2Key).getId());
        }

    };
    private static final VersionedEntityH2Table<Account> ACCOUNT_TABLE = new VersionedEntityH2Table<Account>("account", ACCOUNT_LONG_KEY_FACTORY) {

        @Override
        protected Account load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Account(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Account account) throws SQLException {
            account.save(con);
        }

    };
    private static final H2KeyLongKeyFactory<AccountInfo> ACCOUNT_INFO_DB_KEY_FACTORY = new H2KeyLongKeyFactory<AccountInfo>("account_id") {

        @Override
        public H2Key newKey(AccountInfo accountInfo) {
            return accountInfo.h2Key;
        }

    };
    private static final H2KeyLongKeyFactory<AccountLease> ACCOUNT_LEASE_DB_KEY_FACTORY = new H2KeyLongKeyFactory<AccountLease>("lessor_id") {

        @Override
        public H2Key newKey(AccountLease accountLease) {
            return accountLease.h2Key;
        }

    };
    private static final VersionedEntityH2Table<AccountLease> ACCOUNT_LEASE_TABLE = new VersionedEntityH2Table<AccountLease>("account_lease",
            ACCOUNT_LEASE_DB_KEY_FACTORY) {

        @Override
        protected AccountLease load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new AccountLease(rs, h2Key);
        }

        @Override
        protected void save(Connection con, AccountLease accountLease) throws SQLException {
            accountLease.save(con);
        }

    };
    private static final VersionedEntityH2Table<AccountInfo> ACCOUNT_INFO_TABLE = new VersionedEntityH2Table<AccountInfo>("account_info",
            ACCOUNT_INFO_DB_KEY_FACTORY, "name,description") {

        @Override
        protected AccountInfo load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new AccountInfo(rs, h2Key);
        }

        @Override
        protected void save(Connection con, AccountInfo accountInfo) throws SQLException {
            accountInfo.save(con);
        }

    };
    private static final H2KeyLongKeyFactory<PublicKey> PUBLIC_KEY_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PublicKey>("account_id") {

        @Override
        public H2Key newKey(PublicKey publicKey) {
            return publicKey.h2Key;
        }

        @Override
        public PublicKey newEntity(H2Key h2Key) {
            return new PublicKey(((H2KeyLongKey) h2Key).getId(), null);
        }

    };
    private static final VersionedPersistentH2Table<PublicKey> PUBLIC_KEY_TABLE = new VersionedPersistentH2Table<PublicKey>("public_key", PUBLIC_KEY_DB_KEY_FACTORY) {

        @Override
        protected PublicKey load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PublicKey(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PublicKey publicKey) throws SQLException {
            publicKey.save(con);
        }

    };
    private static final H2KeyLinkKeyFactory<AccountPro> ACCOUNT_ASSET_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<AccountPro>("account_id", "asset_id") {

        @Override
        public H2Key newKey(AccountPro accountPro) {
            return accountPro.h2Key;
        }

    };
    private static final VersionedEntityH2Table<AccountPro> ACCOUNT_ASSET_TABLE = new VersionedEntityH2Table<AccountPro>("account_asset", ACCOUNT_ASSET_DB_KEY_FACTORY) {

        @Override
        protected AccountPro load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new AccountPro(rs, h2Key);
        }

        @Override
        protected void save(Connection con, AccountPro accountPro) throws SQLException {
            accountPro.save(con);
        }

        @Override
        public void trim(int height) {
            super.trim(Math.max(0, height - Constants.EC_MAX_DIVIDEND_PAYMENT_ROLLBACK));
        }

        @Override
        public void checkAvailable(int height) {
            if (height + Constants.EC_MAX_DIVIDEND_PAYMENT_ROLLBACK < EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight()) {
                throw new IllegalArgumentException("Historical data as of height " + height + " not available.");
            }
            if (height > EcBlockchainImpl.getInstance().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " exceeds EC_BLOCKCHAIN height " + EcBlockchainImpl.getInstance().getHeight());
            }
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY quantity DESC, account_id, asset_id ";
        }

    };
    private static final H2KeyLinkKeyFactory<AccountCoin> ACCOUNT_CURRENCY_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<AccountCoin>("account_id", "currency_id") {

        @Override
        public H2Key newKey(AccountCoin accountCoin) {
            return accountCoin.h2Key;
        }

    };
    private static final VersionedEntityH2Table<AccountCoin> ACCOUNT_CURRENCY_TABLE = new VersionedEntityH2Table<AccountCoin>("account_currency", ACCOUNT_CURRENCY_DB_KEY_FACTORY) {

        @Override
        protected AccountCoin load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new AccountCoin(rs, h2Key);
        }

        @Override
        protected void save(Connection con, AccountCoin accountCoin) throws SQLException {
            accountCoin.save(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY units DESC, account_id, currency_id ";
        }

    };
    private static final DerivedH2Table ACCOUNT_GUARANTEED_BALANCE_TABLE = new DerivedH2Table("account_guaranteed_balance") {

        @Override
        public void trim(int height) {
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM account_guaranteed_balance "
                         + "WHERE height < ? AND height >= 0")) {
                pstmtDelete.setInt(1, height - Constants.EC_GUARANTEED_BALANCE_CONFIRMATIONS);
                pstmtDelete.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

    };
    private static final H2KeyLongKeyFactory<AccountProperty> ACCOUNT_PROPERTY_DB_KEY_FACTORY = new H2KeyLongKeyFactory<AccountProperty>("Id") {

        @Override
        public H2Key newKey(AccountProperty accountProperty) {
            return accountProperty.h2Key;
        }

    };
    private static final VersionedEntityH2Table<AccountProperty> ACCOUNT_PROPERTY_TABLE = new VersionedEntityH2Table<AccountProperty>("account_property", ACCOUNT_PROPERTY_DB_KEY_FACTORY) {

        @Override
        protected AccountProperty load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new AccountProperty(rs, h2Key);
        }

        @Override
        protected void save(Connection con, AccountProperty accountProperty) throws SQLException {
            accountProperty.save(con);
        }

    };
    private static final ConcurrentMap<H2Key, byte[]> PUBLIC_KEY_CACHE = PropertiesUtil.getKeyForBoolean("ec.enablePublicKeyCache") ?
            new ConcurrentHashMap<>() : null;
    private static final ListenerManager<Account, Event> EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final ListenerManager<AccountPro, Event> ACCOUNT_ASSET_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final ListenerManager<AccountCoin, Event> ACCOUNT_CURRENCY_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final ListenerManager<AccountLease, Event> ACCOUNT_LEASE_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final ListenerManager<AccountProperty, Event> ACCOUNT_PROPERTY_EVENT_LISTENER_MANAGER = new ListenerManager<>();

    @PostConstruct
    public static void initPostConstruct() {
        EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
            int height = block.getHeight();
            if (height < Constants.EC_TRANSPARENT_FORGING_BLOCK_3) {
                return;
            }
            List<AccountLease> changingLeases = new ArrayList<>();
            try (H2Iterator<AccountLease> leases = getLeaseChangingAccounts(height)) {
                while (leases.hasNext()) {
                    changingLeases.add(leases.next());
                }
            }
            for (AccountLease lease : changingLeases) {
                Account lessor = Account.getAccount(lease.lessorId);
                if (height == lease.currentLeasingHeightFrom) {
                    lessor.activeLesseeId = lease.currentLesseeId;
                    ACCOUNT_LEASE_EVENT_LISTENER_MANAGER.notify(lease, Event.LEASE_STARTED);
                } else if (height == lease.currentLeasingHeightTo) {
                    ACCOUNT_LEASE_EVENT_LISTENER_MANAGER.notify(lease, Event.LEASE_ENDED);
                    lessor.activeLesseeId = 0;
                    if (lease.nextLeasingHeightFrom == 0) {
                        lease.currentLeasingHeightFrom = 0;
                        lease.currentLeasingHeightTo = 0;
                        lease.currentLesseeId = 0;
                        ACCOUNT_LEASE_TABLE.delete(lease);
                    } else {
                        lease.currentLeasingHeightFrom = lease.nextLeasingHeightFrom;
                        lease.currentLeasingHeightTo = lease.nextLeasingHeightTo;
                        lease.currentLesseeId = lease.nextLesseeId;
                        lease.nextLeasingHeightFrom = 0;
                        lease.nextLeasingHeightTo = 0;
                        lease.nextLesseeId = 0;
                        ACCOUNT_LEASE_TABLE.insert(lease);
                        if (height == lease.currentLeasingHeightFrom) {
                            lessor.activeLesseeId = lease.currentLesseeId;
                            ACCOUNT_LEASE_EVENT_LISTENER_MANAGER.notify(lease, Event.LEASE_STARTED);
                        }
                    }
                }
                lessor.save();
            }
        }, EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);

        if (PUBLIC_KEY_CACHE != null) {

            EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
                PUBLIC_KEY_CACHE.remove(ACCOUNT_LONG_KEY_FACTORY.newKey(block.getFoundryId()));
                block.getTransactions().forEach(transaction -> {
                    PUBLIC_KEY_CACHE.remove(ACCOUNT_LONG_KEY_FACTORY.newKey(transaction.getSenderId()));
                    if (!transaction.getAppendages(appendix -> (appendix instanceof PublicKeyAnnouncement), false).isEmpty()) {
                        PUBLIC_KEY_CACHE.remove(ACCOUNT_LONG_KEY_FACTORY.newKey(transaction.getRecipientId()));
                    }
                    if (transaction.getTransactionType() == ShufflingTransaction.SHUFFLING_RECIPIENTS) {
                        Mortgaged.ShufflingRecipients shufflingRecipients = (Mortgaged.ShufflingRecipients) transaction.getAttachment();
                        for (byte[] publicKey : shufflingRecipients.getRecipientPublicKeys()) {
                            PUBLIC_KEY_CACHE.remove(ACCOUNT_LONG_KEY_FACTORY.newKey(Account.getId(publicKey)));
                        }
                    }
                });
            }, EcBlockchainProcessorEvent.BLOCK_POPPED);

            EcBlockchainProcessorImpl.getInstance().addECListener(block -> PUBLIC_KEY_CACHE.clear(), EcBlockchainProcessorEvent.RESCAN_BEGIN);

        }

    }

    private final long id;
    private final H2Key h2Key;
    private PublicKey publicKey;
    private long balanceNQT;
    private long unconfirmedBalanceNQT;
    private long forgedBalanceNQT;
    private long activeLesseeId;
    private Set<ControlType> controls;

    private Account(long id) {
        if (id != Crypto.ecRsDecode(Crypto.ecRsEncode(id))) {
            LoggerUtil.logInfo("CRITICAL ERROR: Reed-Solomon encoding fails for " + id);
        }
        this.id = id;
        this.h2Key = ACCOUNT_LONG_KEY_FACTORY.newKey(this.id);
        this.controls = Collections.emptySet();
    }

    private Account(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.balanceNQT = rs.getLong("balance");
        this.unconfirmedBalanceNQT = rs.getLong("unconfirmed_balance");
        this.forgedBalanceNQT = rs.getLong("forged_balance");
        this.activeLesseeId = rs.getLong("active_lessee_id");
        if (rs.getBoolean("has_control_phasing")) {
            controls = Collections.unmodifiableSet(EnumSet.of(ControlType.PHASING_ONLY));
        } else {
            controls = Collections.emptySet();
        }
    }

    public static boolean addListener(Listener<Account> listener, Event eventType) {
        return EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Account> listener, Event eventType) {
        return EVENT_LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static boolean addPropertysListener(Listener<AccountPro> listener, Event eventType) {
        return ACCOUNT_ASSET_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removePropertysListener(Listener<AccountPro> listener, Event eventType) {
        return ACCOUNT_ASSET_EVENT_LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static boolean addCoinListener(Listener<AccountCoin> listener, Event eventType) {
        return ACCOUNT_CURRENCY_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removeCoinListener(Listener<AccountCoin> listener, Event eventType) {
        return ACCOUNT_CURRENCY_EVENT_LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static boolean addLeaseListener(Listener<AccountLease> listener, Event eventType) {
        return ACCOUNT_LEASE_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removeLeaseListener(Listener<AccountLease> listener, Event eventType) {
        return ACCOUNT_LEASE_EVENT_LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static boolean addPropertyListener(Listener<AccountProperty> listener, Event eventType) {
        return ACCOUNT_PROPERTY_EVENT_LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removePropertyListener(Listener<AccountProperty> listener, Event eventType) {
        return ACCOUNT_PROPERTY_EVENT_LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    public static int getCount() {
        return PUBLIC_KEY_TABLE.getCount();
    }

    public static int getPropertyAccountCount(long assetId) {
        return ACCOUNT_ASSET_TABLE.getCount(new H2ClauseLongClause("asset_id", assetId));
    }

    public static int getPropertyAccountCount(long assetId, int height) {
        return ACCOUNT_ASSET_TABLE.getCount(new H2ClauseLongClause("asset_id", assetId), height);
    }

    public static int getAccountPropertyCount(long accountId) {
        return ACCOUNT_ASSET_TABLE.getCount(new H2ClauseLongClause("account_id", accountId));
    }

    public static int getAccountPropertyCount(long accountId, int height) {
        return ACCOUNT_ASSET_TABLE.getCount(new H2ClauseLongClause("account_id", accountId), height);
    }

    public static int getCoinAccountCount(long currencyId) {
        return ACCOUNT_CURRENCY_TABLE.getCount(new H2ClauseLongClause("currency_id", currencyId));
    }

    public static int getCoinAccountCount(long currencyId, int height) {
        return ACCOUNT_CURRENCY_TABLE.getCount(new H2ClauseLongClause("currency_id", currencyId), height);
    }

    public static int getAccountCoinCount(long accountId) {
        return ACCOUNT_CURRENCY_TABLE.getCount(new H2ClauseLongClause("account_id", accountId));
    }

    public static int getAccountCoinCount(long accountId, int height) {
        return ACCOUNT_CURRENCY_TABLE.getCount(new H2ClauseLongClause("account_id", accountId), height);
    }

    public static int getAccountLeaseCount() {
        return ACCOUNT_LEASE_TABLE.getCount();
    }

    public static int getActiveLeaseCount() {
        return ACCOUNT_TABLE.getCount(new H2ClauseNotNullClause("active_lessee_id"));
    }

    public static AccountProperty getProperty(long propertyId) {
        return ACCOUNT_PROPERTY_TABLE.get(ACCOUNT_PROPERTY_DB_KEY_FACTORY.newKey(propertyId));
    }

    public static H2Iterator<AccountProperty> getProperties(long recipientId, long setterId, String property, int from, int to) {
        if (recipientId == 0 && setterId == 0) {
            throw new IllegalArgumentException("At least one of recipientId and setterId must be specified");
        }
        H2Clause h2Clause = null;
        if (setterId == recipientId) {
            h2Clause = new H2ClauseNullClause("setter_id");
        } else if (setterId != 0) {
            h2Clause = new H2ClauseLongClause("setter_id", setterId);
        }
        if (recipientId != 0) {
            if (h2Clause != null) {
                h2Clause = h2Clause.and(new H2ClauseLongClause("recipient_id", recipientId));
            } else {
                h2Clause = new H2ClauseLongClause("recipient_id", recipientId);
            }
        }
        if (property != null) {
            h2Clause = h2Clause.and(new H2ClauseStringClause("property", property));
        }
        return ACCOUNT_PROPERTY_TABLE.getManyBy(h2Clause, from, to, " ORDER BY property ");
    }

    public static AccountProperty getProperty(long recipientId, String property) {
        return getProperty(recipientId, property, recipientId);
    }

    public static AccountProperty getProperty(long recipientId, String property, long setterId) {
        if (recipientId == 0 || setterId == 0) {
            throw new IllegalArgumentException("Both recipientId and setterId must be specified");
        }
        H2Clause h2Clause = new H2ClauseLongClause("recipient_id", recipientId);
        h2Clause = h2Clause.and(new H2ClauseStringClause("property", property));
        if (setterId != recipientId) {
            h2Clause = h2Clause.and(new H2ClauseLongClause("setter_id", setterId));
        } else {
            h2Clause = h2Clause.and(new H2ClauseNullClause("setter_id"));
        }
        return ACCOUNT_PROPERTY_TABLE.getBy(h2Clause);
    }

    public static Account getAccount(long id) {
        H2Key h2Key = ACCOUNT_LONG_KEY_FACTORY.newKey(id);
        Account account = ACCOUNT_TABLE.get(h2Key);
        if (account == null) {
            PublicKey publicKey = PUBLIC_KEY_TABLE.get(h2Key);
            if (publicKey != null) {
                account = ACCOUNT_TABLE.newEntity(h2Key);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(long id, int height) {
        H2Key h2Key = ACCOUNT_LONG_KEY_FACTORY.newKey(id);
        Account account = ACCOUNT_TABLE.get(h2Key, height);
        if (account == null) {
            PublicKey publicKey = PUBLIC_KEY_TABLE.get(h2Key, height);
            if (publicKey != null) {
                account = ACCOUNT_TABLE.newEntity(h2Key);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(byte[] publicKey) {
        long accountId = getId(publicKey);
        Account account = getAccount(accountId);
        if (account == null) {
            return null;
        }
        if (account.publicKey == null) {
            account.publicKey = PUBLIC_KEY_TABLE.get(ACCOUNT_LONG_KEY_FACTORY.newKey(account));
        }
        if (account.publicKey == null || account.publicKey.publicKey == null || Arrays.equals(account.publicKey.publicKey, publicKey)) {
            return account;
        }
        throw new RuntimeException("DUPLICATE KEY for account " + Long.toUnsignedString(accountId)
                + " existing key " + Convert.toHexString(account.publicKey.publicKey) + " new key " + Convert.toHexString(publicKey));
    }

    public static long getId(byte[] publicKey) {
        byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
        return Convert.fullhashtoid(publicKeyHash);
    }

    public static byte[] getPublicKey(long id) {
        H2Key h2Key = PUBLIC_KEY_DB_KEY_FACTORY.newKey(id);
        byte[] key = null;
        if (PUBLIC_KEY_CACHE != null) {
            key = PUBLIC_KEY_CACHE.get(h2Key);
        }
        if (key == null) {
            PublicKey publicKey = PUBLIC_KEY_TABLE.get(h2Key);
            if (publicKey == null || (key = publicKey.publicKey) == null) {
                return null;
            }
            if (PUBLIC_KEY_CACHE != null) {
                PUBLIC_KEY_CACHE.put(h2Key, key);
            }
        }
        return key;
    }

    static Account addOrGetAccount(long id) {
        if (id == 0) {
            throw new IllegalArgumentException("Invalid accountId 0");
        }
        H2Key h2Key = ACCOUNT_LONG_KEY_FACTORY.newKey(id);
        Account account = ACCOUNT_TABLE.get(h2Key);
        if (account == null) {
            account = ACCOUNT_TABLE.newEntity(h2Key);
            PublicKey publicKey = PUBLIC_KEY_TABLE.get(h2Key);
            if (publicKey == null) {
                publicKey = PUBLIC_KEY_TABLE.newEntity(h2Key);
                PUBLIC_KEY_TABLE.insert(publicKey);
            }
            account.publicKey = publicKey;
        }
        return account;
    }

    private static H2Iterator<AccountLease> getLeaseChangingAccounts(final int height) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement(
                    "SELECT * FROM account_lease WHERE current_leasing_height_from = ? AND latest = TRUE "
                            + "UNION ALL SELECT * FROM account_lease WHERE current_leasing_height_to = ? AND latest = TRUE "
                            + "ORDER BY current_lessee_id, lessor_id");
            int i = 0;
            pstmt.setInt(++i, height);
            pstmt.setInt(++i, height);
            return ACCOUNT_LEASE_TABLE.getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<AccountPro> getAccountPropertys(long accountId, int from, int to) {
        return ACCOUNT_ASSET_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
    }

    public static H2Iterator<AccountPro> getAccountPropertys(long accountId, int height, int from, int to) {
        return ACCOUNT_ASSET_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), height, from, to);
    }

    public static AccountPro getAccountProperty(long accountId, long assetId) {
        return ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(accountId, assetId));
    }

    public static AccountPro getAccountProperty(long accountId, long assetId, int height) {
        return ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(accountId, assetId), height);
    }

    public static H2Iterator<AccountPro> getPropertyAccounts(long assetId, int from, int to) {
        return ACCOUNT_ASSET_TABLE.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to, " ORDER BY quantity DESC, account_id ");
    }

    public static H2Iterator<AccountPro> getPropertyAccounts(long assetId, int height, int from, int to) {
        return ACCOUNT_ASSET_TABLE.getManyBy(new H2ClauseLongClause("asset_id", assetId), height, from, to, " ORDER BY quantity DESC, account_id ");
    }

    public static AccountCoin getAccountCoin(long accountId, long currencyId) {
        return ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(accountId, currencyId));
    }

    public static AccountCoin getAccountCoin(long accountId, long currencyId, int height) {
        return ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(accountId, currencyId), height);
    }

    public static H2Iterator<AccountCoin> getAccountCoins(long accountId, int from, int to) {
        return ACCOUNT_CURRENCY_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), from, to);
    }

    public static H2Iterator<AccountCoin> getAccountCoins(long accountId, int height, int from, int to) {
        return ACCOUNT_CURRENCY_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId), height, from, to);
    }

    public static H2Iterator<AccountCoin> getCoinAccounts(long currencyId, int from, int to) {
        return ACCOUNT_CURRENCY_TABLE.getManyBy(new H2ClauseLongClause("currency_id", currencyId), from, to);
    }

    public static H2Iterator<AccountCoin> getCoinAccounts(long currencyId, int height, int from, int to) {
        return ACCOUNT_CURRENCY_TABLE.getManyBy(new H2ClauseLongClause("currency_id", currencyId), height, from, to);
    }

    public static long getPropertyBalanceQNT(long accountId, long assetId, int height) {
        AccountPro accountPro = ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(accountId, assetId), height);
        return accountPro == null ? 0 : accountPro.quantityQNT;
    }

    public static long getPropertyBalanceQNT(long accountId, long assetId) {
        AccountPro accountPro = ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(accountId, assetId));
        return accountPro == null ? 0 : accountPro.quantityQNT;
    }

    public static long getUnconfirmedPropertyBalanceQNT(long accountId, long assetId) {
        AccountPro accountPro = ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(accountId, assetId));
        return accountPro == null ? 0 : accountPro.unconfirmedQuantityQNT;
    }

    public static long getCoinUnits(long accountId, long currencyId, int height) {
        AccountCoin accountCoin = ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(accountId, currencyId), height);
        return accountCoin == null ? 0 : accountCoin.units;
    }

    public static long getCoinUnits(long accountId, long currencyId) {
        AccountCoin accountCoin = ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(accountId, currencyId));
        return accountCoin == null ? 0 : accountCoin.units;
    }

    public static long getUnconfirmedCoinUnits(long accountId, long currencyId) {
        AccountCoin accountCoin = ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(accountId, currencyId));
        return accountCoin == null ? 0 : accountCoin.unconfirmedUnits;
    }

    public static H2Iterator<AccountInfo> searchAccounts(String query, int from, int to) {
        return ACCOUNT_INFO_TABLE.search(query, H2Clause.EMPTY_CLAUSE, from, to);
    }

    public static void start() {
    }

    public static EncryptedData encryptTo(byte[] publicKey, byte[] data, String senderSecretPhrase, boolean compress) {
        if (compress && data.length > 0) {
            data = Convert.compress(data);
        }
        return EncryptedData.encrypt(data, senderSecretPhrase, publicKey);
    }

    public static byte[] decryptFrom(byte[] publicKey, EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] decrypted = encryptedData.decrypt(recipientSecretPhrase, publicKey);
        if (uncompress && decrypted.length > 0) {
            decrypted = Convert.uncompress(decrypted);
        }
        return decrypted;
    }

    static boolean setOrVerify(long accountId, byte[] key) {
        H2Key h2Key = PUBLIC_KEY_DB_KEY_FACTORY.newKey(accountId);
        PublicKey publicKey = PUBLIC_KEY_TABLE.get(h2Key);
        if (publicKey == null) {
            publicKey = PUBLIC_KEY_TABLE.newEntity(h2Key);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKey.height = EcBlockchainImpl.getInstance().getHeight();
            return true;
        }
        return Arrays.equals(publicKey.publicKey, key);
    }

    private static void checkBalance(long accountId, long confirmed, long unconfirmed) {
        if (accountId == Genesis.EC_CREATOR_ID) {
            return;
        }
        if (confirmed < 0) {
            throw new DoubleSpendingException("Negative balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed < 0) {
            throw new DoubleSpendingException("Negative unconfirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed > confirmed) {
            throw new DoubleSpendingException("Unconfirmed exceeds confirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account (id, "
                + "balance, unconfirmed_balance, forged_balance, "
                + "active_lessee_id, has_control_phasing, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.balanceNQT);
            pstmt.setLong(++i, this.unconfirmedBalanceNQT);
            pstmt.setLong(++i, this.forgedBalanceNQT);
            H2Utils.h2setLongZeroToNull(pstmt, ++i, this.activeLesseeId);
            pstmt.setBoolean(++i, controls.contains(ControlType.PHASING_ONLY));
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    private void save() {
        if (balanceNQT == 0 && unconfirmedBalanceNQT == 0 && forgedBalanceNQT == 0 && activeLesseeId == 0 && controls.isEmpty()) {
            ACCOUNT_TABLE.delete(this, true);
        } else {
            ACCOUNT_TABLE.insert(this);
        }
    }

    public long getId() {
        return id;
    }

    public AccountInfo getAccountInfo() {
        return ACCOUNT_INFO_TABLE.get(ACCOUNT_LONG_KEY_FACTORY.newKey(this));
    }

    void setAccountInfo(String name, String description) {
        name = Convert.emptyToNull(name.trim());
        description = Convert.emptyToNull(description.trim());
        AccountInfo accountInfo = getAccountInfo();
        if (accountInfo == null) {
            accountInfo = new AccountInfo(id, name, description);
        } else {
            accountInfo.name = name;
            accountInfo.description = description;
        }
        accountInfo.save();
    }

    public AccountLease getAccountLease() {
        return ACCOUNT_LEASE_TABLE.get(ACCOUNT_LONG_KEY_FACTORY.newKey(this));
    }

    public EncryptedData encryptTo(byte[] data, String senderSecretPhrase, boolean compress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Recipient account doesn't have a public key set");
        }
        return Account.encryptTo(key, data, senderSecretPhrase, compress);
    }

    public byte[] decryptFrom(EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Sender account doesn't have a public key set");
        }
        return Account.decryptFrom(key, encryptedData, recipientSecretPhrase, uncompress);
    }

    public long getBalanceNQT() {
        return balanceNQT;
    }

    public long getUnconfirmedBalanceNQT() {
        return unconfirmedBalanceNQT;
    }

    public long getForgedBalanceNQT() {
        return forgedBalanceNQT;
    }

    public long getEffectiveBalanceEC() {
        return getEffectiveBalanceEC(EcBlockchainImpl.getInstance().getHeight());
    }

    public long getEffectiveBalanceEC(int height) {
        if (height >= Constants.EC_TRANSPARENT_FORGING_BLOCK_3) {
            if (this.publicKey == null) {
                this.publicKey = PUBLIC_KEY_TABLE.get(ACCOUNT_LONG_KEY_FACTORY.newKey(this));
            }
            if (this.publicKey == null || this.publicKey.publicKey == null || this.publicKey.height == 0 || height - this.publicKey.height <= 1440) {
                return 0; // cfb: Accounts with the public key revealed less than 1440 blocks ago are not allowed to generate blocks
            }
        }
        if (height < Constants.EC_TRANSPARENT_FORGING_BLOCK_1) {
            if (Arrays.binarySearch(Genesis.EC_GENESIS_RECIPIENTS, id) >= 0) {
                return balanceNQT / Constants.ONE_EC;
            }
            long receivedInLastBlock = 0;
            for (Transaction transaction : EcBlockchainImpl.getInstance().getBlockAtHeight(height).getTransactions()) {
                if (id == transaction.getRecipientId()) {
                    receivedInLastBlock += transaction.getAmountNQT();
                }
            }
            return (balanceNQT - receivedInLastBlock) / Constants.ONE_EC;
        }
        EcBlockchainImpl.getInstance().readECLock();
        try {
            long effectiveBalanceNQT = getLessorsGuaranteedBalanceNQT(height);
            if (activeLesseeId == 0) {
                effectiveBalanceNQT += getGuaranteedBalanceNQT(Constants.EC_GUARANTEED_BALANCE_CONFIRMATIONS, height);
            }
            return (height > Constants.EC_SHUFFLING_BLOCK && effectiveBalanceNQT < Constants.EC_MIN_FORGING_BALANCE_NQT) ? 0 : effectiveBalanceNQT / Constants.ONE_EC;
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
    }

    private long getLessorsGuaranteedBalanceNQT(int height) {
        List<Account> lessors = new ArrayList<>();
        try (H2Iterator<Account> iterator = getLessors(height)) {
            while (iterator.hasNext()) {
                lessors.add(iterator.next());
            }
        }
        Long[] lessorIds = new Long[lessors.size()];
        long[] balances = new long[lessors.size()];
        for (int i = 0; i < lessors.size(); i++) {
            lessorIds[i] = lessors.get(i).getId();
            balances[i] = lessors.get(i).getBalanceNQT();
        }
        int blockchainHeight = EcBlockchainImpl.getInstance().getHeight();
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT account_id, SUM (additions) AS additions "
                     + "FROM account_guaranteed_balance, TABLE (id BIGINT=?) T WHERE account_id = T.id AND height > ? "
                     + (height < blockchainHeight ? " AND height <= ? " : "")
                     + " GROUP BY account_id ORDER BY account_id")) {
            pstmt.setObject(1, lessorIds);
            pstmt.setInt(2, height - Constants.EC_GUARANTEED_BALANCE_CONFIRMATIONS);
            if (height < blockchainHeight) {
                pstmt.setInt(3, height);
            }
            long total = 0;
            int i = 0;
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long accountId = rs.getLong("account_id");
                    while (lessorIds[i] < accountId && i < lessorIds.length) {
                        total += balances[i++];
                    }
                    if (lessorIds[i] == accountId) {
                        total += Math.max(balances[i++] - rs.getLong("additions"), 0);
                    }
                }
            }
            while (i < balances.length) {
                total += balances[i++];
            }
            return total;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public H2Iterator<Account> getLessors() {
        return ACCOUNT_TABLE.getManyBy(new H2ClauseLongClause("active_lessee_id", id), 0, -1, " ORDER BY Id ASC ");
    }

    public H2Iterator<Account> getLessors(int height) {
        return ACCOUNT_TABLE.getManyBy(new H2ClauseLongClause("active_lessee_id", id), height, 0, -1, " ORDER BY Id ASC ");
    }

    public long getGuaranteedBalanceNQT() {
        return getGuaranteedBalanceNQT(Constants.EC_GUARANTEED_BALANCE_CONFIRMATIONS, EcBlockchainImpl.getInstance().getHeight());
    }

    public long getGuaranteedBalanceNQT(final int numberOfConfirmations, final int currentHeight) {
        EcBlockchainImpl.getInstance().readECLock();
        try {
            int height = currentHeight - numberOfConfirmations;
            if (height + Constants.EC_GUARANTEED_BALANCE_CONFIRMATIONS < EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight()
                    || height > EcBlockchainImpl.getInstance().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " not available for guaranteed balance calculation");
            }
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT SUM (additions) AS additions "
                         + "FROM account_guaranteed_balance WHERE account_id = ? AND height > ? AND height <= ?")) {
                pstmt.setLong(1, this.id);
                pstmt.setInt(2, height);
                pstmt.setInt(3, currentHeight);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        return balanceNQT;
                    }
                    return Math.max(Math.subtractExact(balanceNQT, rs.getLong("additions")), 0);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        } finally {
            EcBlockchainImpl.getInstance().readECUnlock();
        }
    }

    public H2Iterator<AccountPro> getPropertys(int from, int to) {
        return ACCOUNT_ASSET_TABLE.getManyBy(new H2ClauseLongClause("account_id", this.id), from, to);
    }

    public H2Iterator<AccountPro> getPropertys(int height, int from, int to) {
        return ACCOUNT_ASSET_TABLE.getManyBy(new H2ClauseLongClause("account_id", this.id), height, from, to);
    }

    public H2Iterator<Trade> getTrades(int from, int to) {
        return Trade.getAccountTrades(this.id, from, to);
    }

    public H2Iterator<PropertyTransfer> getPropertyTransfers(int from, int to) {
        return PropertyTransfer.getAccountAssetTransfers(this.id, from, to);
    }

    public H2Iterator<CoinTransfer> getCoinTransfers(int from, int to) {
        return CoinTransfer.getAccountCoinTransfers(this.id, from, to);
    }

    public H2Iterator<Conversion> getExchanges(int from, int to) {
        return Conversion.getAccountConvert(this.id, from, to);
    }

    public AccountPro getAsset(long assetId) {
        return ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(this.id, assetId));
    }

    public AccountPro getAsset(long assetId, int height) {
        return ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(this.id, assetId), height);
    }

    public long getPropertyBalanceQNT(long assetId) {
        return getPropertyBalanceQNT(this.id, assetId);
    }

    public long getPropertyBalanceQNT(long assetId, int height) {
        return getPropertyBalanceQNT(this.id, assetId, height);
    }

    public long getUnconfirmedPropertyBalanceQNT(long assetId) {
        return getUnconfirmedPropertyBalanceQNT(this.id, assetId);
    }

    public AccountCoin getCurrency(long currencyId) {
        return ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(this.id, currencyId));
    }

    public AccountCoin getCurrency(long currencyId, int height) {
        return ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(this.id, currencyId), height);
    }

    public H2Iterator<AccountCoin> getCurrencies(int from, int to) {
        return ACCOUNT_CURRENCY_TABLE.getManyBy(new H2ClauseLongClause("account_id", this.id), from, to);
    }

    public H2Iterator<AccountCoin> getCurrencies(int height, int from, int to) {
        return ACCOUNT_CURRENCY_TABLE.getManyBy(new H2ClauseLongClause("account_id", this.id), height, from, to);
    }

    public long getCoinUnits(long currencyId) {
        return getCoinUnits(this.id, currencyId);
    }

    public long getCoinUnits(long currencyId, int height) {
        return getCoinUnits(this.id, currencyId, height);
    }

    public long getUnconfirmedCoinUnits(long currencyId) {
        return getUnconfirmedCoinUnits(this.id, currencyId);
    }

    public Set<ControlType> getControls() {
        return controls;
    }

    void leaseEffectiveBalance(long lesseeId, int period) {
        int height = EcBlockchainImpl.getInstance().getHeight();
        AccountLease accountLease = ACCOUNT_LEASE_TABLE.get(ACCOUNT_LONG_KEY_FACTORY.newKey(this));
        if (accountLease == null) {
            accountLease = new AccountLease(id,
                    height + Constants.EC_LEASING_DELAY,
                    height + Constants.EC_LEASING_DELAY + period,
                    lesseeId);
        } else if (accountLease.currentLesseeId == 0) {
            accountLease.currentLeasingHeightFrom = height + Constants.EC_LEASING_DELAY;
            accountLease.currentLeasingHeightTo = height + Constants.EC_LEASING_DELAY + period;
            accountLease.currentLesseeId = lesseeId;
        } else {
            accountLease.nextLeasingHeightFrom = height + Constants.EC_LEASING_DELAY;
            if (accountLease.nextLeasingHeightFrom < accountLease.currentLeasingHeightTo) {
                accountLease.nextLeasingHeightFrom = accountLease.currentLeasingHeightTo;
            }
            accountLease.nextLeasingHeightTo = accountLease.nextLeasingHeightFrom + period;
            accountLease.nextLesseeId = lesseeId;
        }
        ACCOUNT_LEASE_TABLE.insert(accountLease);
        ACCOUNT_LEASE_EVENT_LISTENER_MANAGER.notify(accountLease, Event.LEASE_SCHEDULED);
    }

    void addControl(ControlType control) {
        if (controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.of(control);
        newControls.addAll(controls);
        controls = Collections.unmodifiableSet(newControls);
        ACCOUNT_TABLE.insert(this);
    }

    void removeControl(ControlType control) {
        if (!controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.copyOf(controls);
        newControls.remove(control);
        controls = Collections.unmodifiableSet(newControls);
        save();
    }

    void setProperty(Transaction transaction, Account setterAccount, String property, String value) {
        value = Convert.emptyToNull(value);
        AccountProperty accountProperty = getProperty(this.id, property, setterAccount.id);
        if (accountProperty == null) {
            accountProperty = new AccountProperty(transaction.getTransactionId(), this.id, setterAccount.id, property, value);
        } else {
            accountProperty.value = value;
        }
        ACCOUNT_PROPERTY_TABLE.insert(accountProperty);
        EVENT_LISTENER_MANAGER.notify(this, Event.SET_PROPERTY);
        ACCOUNT_PROPERTY_EVENT_LISTENER_MANAGER.notify(accountProperty, Event.SET_PROPERTY);
    }

    void deleteProperty(long propertyId) {
        AccountProperty accountProperty = ACCOUNT_PROPERTY_TABLE.get(ACCOUNT_PROPERTY_DB_KEY_FACTORY.newKey(propertyId));
        if (accountProperty == null) {
            return;
        }
        if (accountProperty.getSetterId() != this.id && accountProperty.getRecipientId() != this.id) {
            throw new RuntimeException("Property " + Long.toUnsignedString(propertyId) + " cannot be deleted by " + Long.toUnsignedString(this.id));
        }
        ACCOUNT_PROPERTY_TABLE.delete(accountProperty);
        EVENT_LISTENER_MANAGER.notify(this, Event.DELETE_PROPERTY);
        ACCOUNT_PROPERTY_EVENT_LISTENER_MANAGER.notify(accountProperty, Event.DELETE_PROPERTY);
    }

    void apply(byte[] key) {
        PublicKey publicKey = PUBLIC_KEY_TABLE.get(h2Key);
        if (publicKey == null) {
            publicKey = PUBLIC_KEY_TABLE.newEntity(h2Key);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            PUBLIC_KEY_TABLE.insert(publicKey);
        } else if (!Arrays.equals(publicKey.publicKey, key)) {
            throw new IllegalStateException("Public key mismatch");
        } else if (publicKey.height >= EcBlockchainImpl.getInstance().getHeight() - 1) {
            PublicKey dbPublicKey = PUBLIC_KEY_TABLE.get(h2Key, false);
            if (dbPublicKey == null || dbPublicKey.publicKey == null) {
                PUBLIC_KEY_TABLE.insert(publicKey);
            }
        }
        if (PUBLIC_KEY_CACHE != null) {
            PUBLIC_KEY_CACHE.put(h2Key, key);
        }
        this.publicKey = publicKey;
    }

    void addToAssetBalanceQNT(LedgerEvent event, long eventId, long assetId, long quantityQNT) {
        if (quantityQNT == 0) {
            return;
        }
        AccountPro accountPro;
        accountPro = ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(this.id, assetId));
        long assetBalance = accountPro == null ? 0 : accountPro.quantityQNT;
        assetBalance = Math.addExact(assetBalance, quantityQNT);
        if (accountPro == null) {
            accountPro = new AccountPro(this.id, assetId, assetBalance, 0);
        } else {
            accountPro.quantityQNT = assetBalance;
        }
        accountPro.save();
        EVENT_LISTENER_MANAGER.notify(this, Event.ASSET_BALANCE);
        ACCOUNT_ASSET_EVENT_LISTENER_MANAGER.notify(accountPro, Event.ASSET_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, false)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id, LedgerHolding.ASSET_BALANCE, assetId,
                    quantityQNT, assetBalance));
        }
    }

    void addToUnconfirmedAssetBalanceQNT(LedgerEvent event, long eventId, long assetId, long quantityQNT) {
        if (quantityQNT == 0) {
            return;
        }
        AccountPro accountPro;
        accountPro = ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(this.id, assetId));
        long unconfirmedAssetBalance = accountPro == null ? 0 : accountPro.unconfirmedQuantityQNT;
        unconfirmedAssetBalance = Math.addExact(unconfirmedAssetBalance, quantityQNT);
        if (accountPro == null) {
            accountPro = new AccountPro(this.id, assetId, 0, unconfirmedAssetBalance);
        } else {
            accountPro.unconfirmedQuantityQNT = unconfirmedAssetBalance;
        }
        accountPro.save();
        EVENT_LISTENER_MANAGER.notify(this, Event.UNCONFIRMED_ASSET_BALANCE);
        ACCOUNT_ASSET_EVENT_LISTENER_MANAGER.notify(accountPro, Event.UNCONFIRMED_ASSET_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                    LedgerHolding.UNCONFIRMED_ASSET_BALANCE, assetId,
                    quantityQNT, unconfirmedAssetBalance));
        }
    }

    void addToAssetAndUnconfirmedAssetBalanceQNT(LedgerEvent event, long eventId, long assetId, long quantityQNT) {
        if (quantityQNT == 0) {
            return;
        }
        AccountPro accountPro;
        accountPro = ACCOUNT_ASSET_TABLE.get(ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(this.id, assetId));
        long assetBalance = accountPro == null ? 0 : accountPro.quantityQNT;
        assetBalance = Math.addExact(assetBalance, quantityQNT);
        long unconfirmedAssetBalance = accountPro == null ? 0 : accountPro.unconfirmedQuantityQNT;
        unconfirmedAssetBalance = Math.addExact(unconfirmedAssetBalance, quantityQNT);
        if (accountPro == null) {
            accountPro = new AccountPro(this.id, assetId, assetBalance, unconfirmedAssetBalance);
        } else {
            accountPro.quantityQNT = assetBalance;
            accountPro.unconfirmedQuantityQNT = unconfirmedAssetBalance;
        }
        accountPro.save();
        EVENT_LISTENER_MANAGER.notify(this, Event.ASSET_BALANCE);
        EVENT_LISTENER_MANAGER.notify(this, Event.UNCONFIRMED_ASSET_BALANCE);
        ACCOUNT_ASSET_EVENT_LISTENER_MANAGER.notify(accountPro, Event.ASSET_BALANCE);
        ACCOUNT_ASSET_EVENT_LISTENER_MANAGER.notify(accountPro, Event.UNCONFIRMED_ASSET_BALANCE);
        if (event == null) {
            return; // do not try to log ledger entry for FXT distribution
        }
        if (AccountLedger.mustLogEntry(this.id, true)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                    LedgerHolding.UNCONFIRMED_ASSET_BALANCE, assetId,
                    quantityQNT, unconfirmedAssetBalance));
        }
        if (AccountLedger.mustLogEntry(this.id, false)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                    LedgerHolding.ASSET_BALANCE, assetId,
                    quantityQNT, assetBalance));
        }
    }

    void addToCurrencyUnits(LedgerEvent event, long eventId, long currencyId, long units) {
        if (units == 0) {
            return;
        }
        AccountCoin accountCoin;
        accountCoin = ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(this.id, currencyId));
        long currencyUnits = accountCoin == null ? 0 : accountCoin.units;
        currencyUnits = Math.addExact(currencyUnits, units);
        if (accountCoin == null) {
            accountCoin = new AccountCoin(this.id, currencyId, currencyUnits, 0);
        } else {
            accountCoin.units = currencyUnits;
        }
        accountCoin.save();
        EVENT_LISTENER_MANAGER.notify(this, Event.CURRENCY_BALANCE);
        ACCOUNT_CURRENCY_EVENT_LISTENER_MANAGER.notify(accountCoin, Event.CURRENCY_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, false)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id, LedgerHolding.CURRENCY_BALANCE, currencyId,
                    units, currencyUnits));
        }
    }

    void addToUnconfirmedCurrencyUnits(LedgerEvent event, long eventId, long currencyId, long units) {
        if (units == 0) {
            return;
        }
        AccountCoin accountCoin = ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(this.id, currencyId));
        long unconfirmedCurrencyUnits = accountCoin == null ? 0 : accountCoin.unconfirmedUnits;
        unconfirmedCurrencyUnits = Math.addExact(unconfirmedCurrencyUnits, units);
        if (accountCoin == null) {
            accountCoin = new AccountCoin(this.id, currencyId, 0, unconfirmedCurrencyUnits);
        } else {
            accountCoin.unconfirmedUnits = unconfirmedCurrencyUnits;
        }
        accountCoin.save();
        EVENT_LISTENER_MANAGER.notify(this, Event.UNCONFIRMED_CURRENCY_BALANCE);
        ACCOUNT_CURRENCY_EVENT_LISTENER_MANAGER.notify(accountCoin, Event.UNCONFIRMED_CURRENCY_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                    LedgerHolding.UNCONFIRMED_CURRENCY_BALANCE, currencyId,
                    units, unconfirmedCurrencyUnits));
        }
    }

    void addToCurrencyAndUnconfirmedCurrencyUnits(LedgerEvent event, long eventId, long currencyId, long units) {
        if (units == 0) {
            return;
        }
        AccountCoin accountCoin;
        accountCoin = ACCOUNT_CURRENCY_TABLE.get(ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(this.id, currencyId));
        long currencyUnits = accountCoin == null ? 0 : accountCoin.units;
        currencyUnits = Math.addExact(currencyUnits, units);
        long unconfirmedCurrencyUnits = accountCoin == null ? 0 : accountCoin.unconfirmedUnits;
        unconfirmedCurrencyUnits = Math.addExact(unconfirmedCurrencyUnits, units);
        if (accountCoin == null) {
            accountCoin = new AccountCoin(this.id, currencyId, currencyUnits, unconfirmedCurrencyUnits);
        } else {
            accountCoin.units = currencyUnits;
            accountCoin.unconfirmedUnits = unconfirmedCurrencyUnits;
        }
        accountCoin.save();
        EVENT_LISTENER_MANAGER.notify(this, Event.CURRENCY_BALANCE);
        EVENT_LISTENER_MANAGER.notify(this, Event.UNCONFIRMED_CURRENCY_BALANCE);
        ACCOUNT_CURRENCY_EVENT_LISTENER_MANAGER.notify(accountCoin, Event.CURRENCY_BALANCE);
        ACCOUNT_CURRENCY_EVENT_LISTENER_MANAGER.notify(accountCoin, Event.UNCONFIRMED_CURRENCY_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                    LedgerHolding.UNCONFIRMED_CURRENCY_BALANCE, currencyId,
                    units, unconfirmedCurrencyUnits));
        }
        if (AccountLedger.mustLogEntry(this.id, false)) {
            AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                    LedgerHolding.CURRENCY_BALANCE, currencyId,
                    units, currencyUnits));
        }
    }

    void addToBalanceNQT(LedgerEvent event, long eventId, long amountNQT) {
        addToBalanceNQT(event, eventId, amountNQT, 0);
    }

    void addToBalanceNQT(LedgerEvent event, long eventId, long amountNQT, long feeNQT) {
        if (amountNQT == 0 && feeNQT == 0) {
            return;
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        this.balanceNQT = Math.addExact(this.balanceNQT, totalAmountNQT);
        addToGuaranteedBalanceNQT(totalAmountNQT);
        checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
        save();
        EVENT_LISTENER_MANAGER.notify(this, Event.BALANCE);
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.EC_BALANCE, null, feeNQT, this.balanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                        LedgerHolding.EC_BALANCE, null, amountNQT, this.balanceNQT));
            }
        }
    }

    void addToUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT) {
        addToUnconfirmedBalanceNQT(event, eventId, amountNQT, 0);
    }

    void addToUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT, long feeNQT) {
        if (amountNQT == 0 && feeNQT == 0) {
            return;
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        this.unconfirmedBalanceNQT = Math.addExact(this.unconfirmedBalanceNQT, totalAmountNQT);
        checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
        save();
        EVENT_LISTENER_MANAGER.notify(this, Event.UNCONFIRMED_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_EC_BALANCE, null, feeNQT, this.unconfirmedBalanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_EC_BALANCE, null, amountNQT, this.unconfirmedBalanceNQT));
            }
        }
    }

    void addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT) {
        addToBalanceAndUnconfirmedBalanceNQT(event, eventId, amountNQT, 0);
    }

    void addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT, long feeNQT) {
        if (amountNQT == 0 && feeNQT == 0) {
            return;
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        this.balanceNQT = Math.addExact(this.balanceNQT, totalAmountNQT);
        this.unconfirmedBalanceNQT = Math.addExact(this.unconfirmedBalanceNQT, totalAmountNQT);
        addToGuaranteedBalanceNQT(totalAmountNQT);
        checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
        save();
        EVENT_LISTENER_MANAGER.notify(this, Event.BALANCE);
        EVENT_LISTENER_MANAGER.notify(this, Event.UNCONFIRMED_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_EC_BALANCE, null, feeNQT, this.unconfirmedBalanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_EC_BALANCE, null, amountNQT, this.unconfirmedBalanceNQT));
            }
        }
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.EC_BALANCE, null, feeNQT, this.balanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new AccountLedger.LedgerEntry(event, eventId, this.id,
                        LedgerHolding.EC_BALANCE, null, amountNQT, this.balanceNQT));
            }
        }
    }

    void addToForgedBalanceNQT(long amountNQT) {
        if (amountNQT == 0) {
            return;
        }
        this.forgedBalanceNQT = Math.addExact(this.forgedBalanceNQT, amountNQT);
        save();
    }

    private void addToGuaranteedBalanceNQT(long amountNQT) {
        if (amountNQT <= 0) {
            return;
        }
        int blockchainHeight = EcBlockchainImpl.getInstance().getHeight();
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT additions FROM account_guaranteed_balance "
                     + "WHERE account_id = ? and height = ?");
             PreparedStatement pstmtUpdate = con.prepareStatement("MERGE INTO account_guaranteed_balance (account_id, "
                     + " additions, height) KEY (account_id, height) VALUES(?, ?, ?)")) {
            pstmtSelect.setLong(1, this.id);
            pstmtSelect.setInt(2, blockchainHeight);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                long additions = amountNQT;
                if (rs.next()) {
                    additions = Math.addExact(additions, rs.getLong("additions"));
                }
                pstmtUpdate.setLong(1, this.id);
                pstmtUpdate.setLong(2, additions);
                pstmtUpdate.setInt(3, blockchainHeight);
                pstmtUpdate.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    void payDividends(final long transactionId, Mortgaged.ColoredCoinsDividendPayment attachment) {
        long totalDividend = 0;
        List<AccountPro> accountPros = new ArrayList<>();
        try (H2Iterator<AccountPro> iterator = getPropertyAccounts(attachment.getAssetId(), attachment.getHeight(), 0, -1)) {
            while (iterator.hasNext()) {
                accountPros.add(iterator.next());
            }
        }
        final long amountNQTPerQNT = attachment.getAmountNQTPerQNT();
        long numAccounts = 0;
        for (final AccountPro accountPro : accountPros) {
            if (accountPro.getAccountId() != this.id && accountPro.getQuantityQNT() != 0) {
                long dividend = Math.multiplyExact(accountPro.getQuantityQNT(), amountNQTPerQNT);
                Account.getAccount(accountPro.getAccountId())
                        .addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.ASSET_DIVIDEND_PAYMENT, transactionId, dividend);
                totalDividend += dividend;
                numAccounts += 1;
            }
        }
        this.addToBalanceNQT(LedgerEvent.ASSET_DIVIDEND_PAYMENT, transactionId, -totalDividend);
        PropertyDividend.addPropertyDividend(transactionId, attachment, totalDividend, numAccounts);
    }

    @Override
    public String toString() {
        return "Account " + Long.toUnsignedString(getId());
    }

    public static final class AccountPro {

        private final long accountId;
        private final long assetId;
        private final H2Key h2Key;
        private long quantityQNT;
        private long unconfirmedQuantityQNT;

        private AccountPro(long accountId, long assetId, long quantityQNT, long unconfirmedQuantityQNT) {
            this.accountId = accountId;
            this.assetId = assetId;
            this.h2Key = ACCOUNT_ASSET_DB_KEY_FACTORY.newKey(this.accountId, this.assetId);
            this.quantityQNT = quantityQNT;
            this.unconfirmedQuantityQNT = unconfirmedQuantityQNT;
        }

        private AccountPro(ResultSet rs, H2Key h2Key) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.assetId = rs.getLong("asset_id");
            this.h2Key = h2Key;
            this.quantityQNT = rs.getLong("quantity");
            this.unconfirmedQuantityQNT = rs.getLong("unconfirmed_quantity");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_asset "
                    + "(account_id, asset_id, quantity, unconfirmed_quantity, height, latest) "
                    + "KEY (account_id, asset_id, height) VALUES (?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                pstmt.setLong(++i, this.assetId);
                pstmt.setLong(++i, this.quantityQNT);
                pstmt.setLong(++i, this.unconfirmedQuantityQNT);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public long getAssetId() {
            return assetId;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

        public long getUnconfirmedQuantityQNT() {
            return unconfirmedQuantityQNT;
        }

        private void save() {
            checkBalance(this.accountId, this.quantityQNT, this.unconfirmedQuantityQNT);
            if (this.quantityQNT > 0 || this.unconfirmedQuantityQNT > 0) {
                ACCOUNT_ASSET_TABLE.insert(this);
            } else {
                ACCOUNT_ASSET_TABLE.delete(this);
            }
        }

        @Override
        public String toString() {
            return "AccountPro account_id: " + Long.toUnsignedString(accountId) + " asset_id: " + Long.toUnsignedString(assetId)
                    + " quantity: " + quantityQNT + " unconfirmedQuantity: " + unconfirmedQuantityQNT;
        }

    }

    @SuppressWarnings("UnusedDeclaration")
    public static final class AccountCoin {

        private final long accountId;
        private final long currencyId;
        private final H2Key h2Key;
        private long units;
        private long unconfirmedUnits;

        private AccountCoin(long accountId, long currencyId, long quantityQNT, long unconfirmedQuantityQNT) {
            this.accountId = accountId;
            this.currencyId = currencyId;
            this.h2Key = ACCOUNT_CURRENCY_DB_KEY_FACTORY.newKey(this.accountId, this.currencyId);
            this.units = quantityQNT;
            this.unconfirmedUnits = unconfirmedQuantityQNT;
        }

        private AccountCoin(ResultSet rs, H2Key h2Key) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.currencyId = rs.getLong("currency_id");
            this.h2Key = h2Key;
            this.units = rs.getLong("units");
            this.unconfirmedUnits = rs.getLong("unconfirmed_units");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_currency "
                    + "(account_id, currency_id, units, unconfirmed_units, height, latest) "
                    + "KEY (account_id, currency_id, height) VALUES (?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                pstmt.setLong(++i, this.currencyId);
                pstmt.setLong(++i, this.units);
                pstmt.setLong(++i, this.unconfirmedUnits);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public long getCurrencyId() {
            return currencyId;
        }

        public long getUnits() {
            return units;
        }

        public long getUnconfirmedUnits() {
            return unconfirmedUnits;
        }

        private void save() {
            checkBalance(this.accountId, this.units, this.unconfirmedUnits);
            if (this.units > 0 || this.unconfirmedUnits > 0) {
                ACCOUNT_CURRENCY_TABLE.insert(this);
            } else if (this.units == 0 && this.unconfirmedUnits == 0) {
                ACCOUNT_CURRENCY_TABLE.delete(this);
            }
        }

        @Override
        public String toString() {
            return "AccountCoin account_id: " + Long.toUnsignedString(accountId) + " currency_id: " + Long.toUnsignedString(currencyId)
                    + " quantity: " + units + " unconfirmedQuantity: " + unconfirmedUnits;
        }

    }

    public static final class AccountLease {

        private final long lessorId;
        private final H2Key h2Key;
        private long currentLesseeId;
        private int currentLeasingHeightFrom;
        private int currentLeasingHeightTo;
        private long nextLesseeId;
        private int nextLeasingHeightFrom;
        private int nextLeasingHeightTo;

        private AccountLease(long lessorId,
                             int currentLeasingHeightFrom, int currentLeasingHeightTo, long currentLesseeId) {
            this.lessorId = lessorId;
            this.h2Key = ACCOUNT_LEASE_DB_KEY_FACTORY.newKey(this.lessorId);
            this.currentLeasingHeightFrom = currentLeasingHeightFrom;
            this.currentLeasingHeightTo = currentLeasingHeightTo;
            this.currentLesseeId = currentLesseeId;
        }

        private AccountLease(ResultSet rs, H2Key h2Key) throws SQLException {
            this.lessorId = rs.getLong("lessor_id");
            this.h2Key = h2Key;
            this.currentLeasingHeightFrom = rs.getInt("current_leasing_height_from");
            this.currentLeasingHeightTo = rs.getInt("current_leasing_height_to");
            this.currentLesseeId = rs.getLong("current_lessee_id");
            this.nextLeasingHeightFrom = rs.getInt("next_leasing_height_from");
            this.nextLeasingHeightTo = rs.getInt("next_leasing_height_to");
            this.nextLesseeId = rs.getLong("next_lessee_id");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_lease "
                    + "(lessor_id, current_leasing_height_from, current_leasing_height_to, current_lessee_id, "
                    + "next_leasing_height_from, next_leasing_height_to, next_lessee_id, height, latest) "
                    + "KEY (lessor_id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.lessorId);
                H2Utils.h2setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightFrom);
                H2Utils.h2setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightTo);
                H2Utils.h2setLongZeroToNull(pstmt, ++i, this.currentLesseeId);
                H2Utils.h2setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightFrom);
                H2Utils.h2setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightTo);
                H2Utils.h2setLongZeroToNull(pstmt, ++i, this.nextLesseeId);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getLessorId() {
            return lessorId;
        }

        public long getCurrentLesseeId() {
            return currentLesseeId;
        }

        public int getCurrentLeasingHeightFrom() {
            return currentLeasingHeightFrom;
        }

        public int getCurrentLeasingHeightTo() {
            return currentLeasingHeightTo;
        }

        public long getNextLesseeId() {
            return nextLesseeId;
        }

        public int getNextLeasingHeightFrom() {
            return nextLeasingHeightFrom;
        }

        public int getNextLeasingHeightTo() {
            return nextLeasingHeightTo;
        }

    }

    public static final class AccountInfo {

        private final long accountId;
        private final H2Key h2Key;
        private String name;
        private String description;

        private AccountInfo(long accountId, String name, String description) {
            this.accountId = accountId;
            this.h2Key = ACCOUNT_INFO_DB_KEY_FACTORY.newKey(this.accountId);
            this.name = name;
            this.description = description;
        }

        private AccountInfo(ResultSet rs, H2Key h2Key) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.h2Key = h2Key;
            this.name = rs.getString("name");
            this.description = rs.getString("description");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_info "
                    + "(account_id, name, description, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                H2Utils.h2setString(pstmt, ++i, this.name);
                H2Utils.h2setString(pstmt, ++i, this.description);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        private void save() {
            if (this.name != null || this.description != null) {
                ACCOUNT_INFO_TABLE.insert(this);
            } else {
                ACCOUNT_INFO_TABLE.delete(this);
            }
        }

    }

    public static final class AccountProperty {

        private final long id;
        private final H2Key h2Key;
        private final long recipientId;
        private final long setterId;
        private String property;
        private String value;

        private AccountProperty(long id, long recipientId, long setterId, String property, String value) {
            this.id = id;
            this.h2Key = ACCOUNT_PROPERTY_DB_KEY_FACTORY.newKey(this.id);
            this.recipientId = recipientId;
            this.setterId = setterId;
            this.property = property;
            this.value = value;
        }

        private AccountProperty(ResultSet rs, H2Key h2Key) throws SQLException {
            this.id = rs.getLong("Id");
            this.h2Key = h2Key;
            this.recipientId = rs.getLong("recipient_id");
            long setterId = rs.getLong("setter_id");
            this.setterId = setterId == 0 ? recipientId : setterId;
            this.property = rs.getString("property");
            this.value = rs.getString("value");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_property "
                    + "(id, recipient_id, setter_id, property, value, height, latest) "
                    + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.recipientId);
                H2Utils.h2setLongZeroToNull(pstmt, ++i, this.setterId != this.recipientId ? this.setterId : 0);
                H2Utils.h2setString(pstmt, ++i, this.property);
                H2Utils.h2setString(pstmt, ++i, this.value);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getRecipientId() {
            return recipientId;
        }

        public long getSetterId() {
            return setterId;
        }

        public String getProperty() {
            return property;
        }

        public String getValue() {
            return value;
        }

    }

    public static final class PublicKey {

        private final long accountId;
        private final H2Key h2Key;
        private byte[] publicKey;
        private int height;

        private PublicKey(long accountId, byte[] publicKey) {
            this.accountId = accountId;
            this.h2Key = PUBLIC_KEY_DB_KEY_FACTORY.newKey(accountId);
            this.publicKey = publicKey;
            this.height = EcBlockchainImpl.getInstance().getHeight();
        }

        private PublicKey(ResultSet rs, H2Key h2Key) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.h2Key = h2Key;
            this.publicKey = rs.getBytes("public_key");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            height = EcBlockchainImpl.getInstance().getHeight();
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO public_key (account_id, public_key, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, accountId);
                H2Utils.h2setBytes(pstmt, ++i, publicKey);
                pstmt.setInt(++i, height);
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public int getHeight() {
            return height;
        }

    }

}
