package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.H2.H2Utils;
import com.inesv.ecchain.kernel.H2.DerivedH2Table;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.*;

public class AccountLedger {

    private static final EcBlockchain EC_BLOCKCHAIN = EcBlockchainImpl.getInstance();

    private static final EcBlockchainProcessor BLOCKCHAIN_PROCESSOR = EcBlockchainProcessorImpl.getInstance();

    private static final List<LedgerEntry> PENDING_ENTRIES = new ArrayList<>();

    private static final AccountLedgerTable ACCOUNT_LEDGER_TABLE = new AccountLedgerTable();

    private static final ListenerManager<LedgerEntry, AccountLedgerEvent> LISTENER_MANAGER = new ListenerManager<>();

    private static final SortedSet<Long> TRACK_ACCOUNTS = new TreeSet<>();

    private static boolean ledgerenabled;

    private static boolean trackallaccounts;

    private static int logunconfirmed;

    @PostConstruct
    public static void initPostConstruct() {
        List<String> ledgerAccounts = PropertiesUtil.getStringListProperty("ec.ledgerAccounts");
        ledgerenabled = !ledgerAccounts.isEmpty();
        trackallaccounts = ledgerAccounts.contains("*");
        if (ledgerenabled) {
            if (trackallaccounts) {
                LoggerUtil.logInfo("Account ledger is tracking all accounts");
            } else {
                for (String account : ledgerAccounts) {
                    try {
                        TRACK_ACCOUNTS.add(Convert.parseAccountId(account));
                        LoggerUtil.logInfo("Account ledger is tracking account " + account);
                    } catch (RuntimeException e) {
                        LoggerUtil.logError("Account " + account + " is not valid; ignored");
                    }
                }
            }
        } else {
            LoggerUtil.logInfo("Account ledger is not enabled");
        }
        int temp = PropertiesUtil.getKeyForInt("ec.ledgerLogUnconfirmed", 1);
        logunconfirmed = (temp >= 0 && temp <= 2 ? temp : 1);
    }

    public static void start() {
    }

    public static boolean addAccountLedgerListener(Listener<LedgerEntry> listener, AccountLedgerEvent eventType) {
        return LISTENER_MANAGER.addECListener(listener, eventType);
    }

    public static boolean removeAccountLedgerListener(Listener<LedgerEntry> listener, AccountLedgerEvent eventType) {
        return LISTENER_MANAGER.removeECListener(listener, eventType);
    }

    static boolean mustLogEntry(long accountId, boolean isUnconfirmed) {
        //
        // Must be tracking this account
        //
        if (!ledgerenabled || (!trackallaccounts && !TRACK_ACCOUNTS.contains(accountId))) {
            return false;
        }
        // confirmed changes only occur while processing block, and unconfirmed changes are
        // only logged while processing block
        if (!BLOCKCHAIN_PROCESSOR.isProcessingBlock()) {
            return false;
        }
        //
        // Log unconfirmed changes only when processing a block and logunconfirmed does not equal 0
        // Log confirmed changes unless logunconfirmed equals 2
        //
        if (isUnconfirmed && logunconfirmed == 0) {
            return false;
        }
        if (!isUnconfirmed && logunconfirmed == 2) {
            return false;
        }
        if (Constants.TRIM_KEEP > 0 && EC_BLOCKCHAIN.getHeight() <= Constants.EC_LAST_KNOWN_BLOCK - Constants.TRIM_KEEP) {
            return false;
        }
        //
        // Don't log account changes if we are scanning the EC_BLOCKCHAIN and the current height
        // is less than the minimum account_ledger trim height
        //
        if (BLOCKCHAIN_PROCESSOR.isScanning() && Constants.TRIM_KEEP > 0 &&
                EC_BLOCKCHAIN.getHeight() <= BLOCKCHAIN_PROCESSOR.getInitialScanHeight() - Constants.TRIM_KEEP) {
            return false;
        }
        return true;
    }

    static void logEntry(LedgerEntry ledgerEntry) {
        //
        // Must be in a database transaction
        //
        if (!H2.H2.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        //
        // Combine multiple ledger entries
        //
        int index = PENDING_ENTRIES.indexOf(ledgerEntry);
        if (index >= 0) {
            LedgerEntry existingEntry = PENDING_ENTRIES.remove(index);
            ledgerEntry.updateChange(existingEntry.getChange());
            long adjustedBalance = existingEntry.getBalance() - existingEntry.getChange();
            for (; index < PENDING_ENTRIES.size(); index++) {
                existingEntry = PENDING_ENTRIES.get(index);
                if (existingEntry.getAccountId() == ledgerEntry.getAccountId() &&
                        existingEntry.getHolding() == ledgerEntry.getHolding() &&
                        ((existingEntry.getHoldingId() == null && ledgerEntry.getHoldingId() == null) ||
                                (existingEntry.getHoldingId() != null && existingEntry.getHoldingId().equals(ledgerEntry.getHoldingId())))) {
                    adjustedBalance += existingEntry.getChange();
                    existingEntry.setBalance(adjustedBalance);
                }
            }
        }
        PENDING_ENTRIES.add(ledgerEntry);
    }

    static void commitEntries() {
        for (LedgerEntry ledgerEntry : PENDING_ENTRIES) {
            ACCOUNT_LEDGER_TABLE.insert(ledgerEntry);
            LISTENER_MANAGER.notify(ledgerEntry, AccountLedgerEvent.ADD_ENTRY);
        }
        PENDING_ENTRIES.clear();
    }

    static void clearEntries() {
        PENDING_ENTRIES.clear();
    }

    public static LedgerEntry getEntry(long ledgerId) {
        if (!ledgerenabled) {
            return null;
        }
        LedgerEntry entry;
        try (Connection con = H2.H2.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT * FROM account_ledger WHERE db_id = ?")) {
            stmt.setLong(1, ledgerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    entry = new LedgerEntry(rs);
                } else {
                    entry = null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return entry;
    }

    public static List<LedgerEntry> getEntries(long accountId, LedgerEvent event, long eventId,
                                               LedgerHolding holding, long holdingId,
                                               int firstIndex, int lastIndex) {
        if (!ledgerenabled) {
            return Collections.emptyList();
        }
        List<LedgerEntry> entryList = new ArrayList<>();
        //
        // Build the SELECT statement to search the entries
        StringBuilder sb = new StringBuilder(128);
        sb.append("SELECT * FROM account_ledger ");
        if (accountId != 0 || event != null || holding != null) {
            sb.append("WHERE ");
        }
        if (accountId != 0) {
            sb.append("account_id = ? ");
        }
        if (event != null) {
            if (accountId != 0) {
                sb.append("AND ");
            }
            sb.append("event_type = ? ");
            if (eventId != 0) {
                sb.append("AND event_id = ? ");
            }
        }
        if (holding != null) {
            if (accountId != 0 || event != null) {
                sb.append("AND ");
            }
            sb.append("holding_type = ? ");
            if (holdingId != 0) {
                sb.append("AND holding_id = ? ");
            }
        }
        sb.append("ORDER BY db_id DESC ");
        sb.append(H2Utils.limitsClause(firstIndex, lastIndex));
        //
        // Get the ledger entries
        //
        EC_BLOCKCHAIN.readECLock();
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sb.toString())) {
            int i = 0;
            if (accountId != 0) {
                pstmt.setLong(++i, accountId);
            }
            if (event != null) {
                pstmt.setByte(++i, (byte) event.getCode());
                if (eventId != 0) {
                    pstmt.setLong(++i, eventId);
                }
            }
            if (holding != null) {
                pstmt.setByte(++i, (byte) holding.getCode());
                if (holdingId != 0) {
                    pstmt.setLong(++i, holdingId);
                }
            }
            H2Utils.setLimits(++i, pstmt, firstIndex, lastIndex);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entryList.add(new LedgerEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            EC_BLOCKCHAIN.readECUnlock();
        }
        return entryList;
    }

    private static class AccountLedgerTable extends DerivedH2Table {

        /**
         * Create the account ledger table
         */
        public AccountLedgerTable() {
            super("account_ledger");
        }

        /**
         * Insert an entry into the table
         *
         * @param ledgerEntry Ledger entry
         */
        public void insert(LedgerEntry ledgerEntry) {
            try (Connection con = h2.getConnection()) {
                ledgerEntry.save(con);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        /**
         * Trim the account ledger table
         *
         * @param height Trim height
         */
        @Override
        public void trim(int height) {
            if (Constants.TRIM_KEEP <= 0) {
                return;
            }
            try (Connection con = h2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("DELETE FROM account_ledger WHERE height <= ?")) {
                int trimHeight = Math.max(EC_BLOCKCHAIN.getHeight() - Constants.TRIM_KEEP, 0);
                pstmt.setInt(1, trimHeight);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    }

    public static class LedgerEntry {

        /**
         * Ledger event
         */
        private final LedgerEvent event;
        /**
         * Associated event identifier
         */
        private final long eventId;
        /**
         * Account identifier
         */
        private final long accountId;
        /**
         * Holding
         */
        private final LedgerHolding holding;
        /**
         * Holding identifier
         */
        private final Long holdingId;
        /**
         * EcBlock identifier
         */
        private final long blockId;
        /**
         * EcBlockchain height
         */
        private final int height;
        /**
         * EcBlock timestamp
         */
        private final int timestamp;
        /**
         * Ledger identifier
         */
        private long ledgerId = -1;
        /**
         * Change in balance
         */
        private long change;
        /**
         * New balance
         */
        private long balance;

        /**
         * Create a ledger entry
         *
         * @param event     Event
         * @param eventId   Event identifier
         * @param accountId Account identifier
         * @param holding   Holding or null
         * @param holdingId Holding identifier or null
         * @param change    Change in balance
         * @param balance   New balance
         */
        public LedgerEntry(LedgerEvent event, long eventId, long accountId, LedgerHolding holding, Long holdingId,
                           long change, long balance) {
            this.event = event;
            this.eventId = eventId;
            this.accountId = accountId;
            this.holding = holding;
            this.holdingId = holdingId;
            this.change = change;
            this.balance = balance;
            EcBlock ecBlock = EC_BLOCKCHAIN.getLastECBlock();
            this.blockId = ecBlock.getECId();
            this.height = ecBlock.getHeight();
            this.timestamp = ecBlock.getTimestamp();
        }

        /**
         * Create a ledger entry
         *
         * @param event     Event
         * @param eventId   Event identifier
         * @param accountId Account identifier
         * @param change    Change in balance
         * @param balance   New balance
         */
        public LedgerEntry(LedgerEvent event, long eventId, long accountId, long change, long balance) {
            this(event, eventId, accountId, null, null, change, balance);
        }

        /**
         * Create a ledger entry from a database entry
         *
         * @param rs Result set
         * @throws SQLException Database error occurred
         */
        private LedgerEntry(ResultSet rs) throws SQLException {
            ledgerId = rs.getLong("db_id");
            event = LedgerEvent.fromCode(rs.getByte("event_type"));
            eventId = rs.getLong("event_id");
            accountId = rs.getLong("account_id");
            int holdingType = rs.getByte("holding_type");
            if (holdingType >= 0) {
                holding = LedgerHolding.fromCode(holdingType);
            } else {
                holding = null;
            }
            long id = rs.getLong("holding_id");
            if (rs.wasNull()) {
                holdingId = null;
            } else {
                holdingId = id;
            }
            change = rs.getLong("change");
            balance = rs.getLong("balance");
            blockId = rs.getLong("block_id");
            height = rs.getInt("height");
            timestamp = rs.getInt("timestamp");
        }

        /**
         * Return the ledger identifier
         *
         * @return Ledger identifier or -1 if not set
         */
        public long getLedgerId() {
            return ledgerId;
        }

        /**
         * Return the ledger event
         *
         * @return Ledger event
         */
        public LedgerEvent getEvent() {
            return event;
        }

        /**
         * Return the associated event identifier
         *
         * @return Event identifier
         */
        public long getEventId() {
            return eventId;
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
         * Return the holding
         *
         * @return Holding or null if there is no holding
         */
        public LedgerHolding getHolding() {
            return holding;
        }

        /**
         * Return the holding identifier
         *
         * @return Holding identifier or null if there is no holding identifier
         */
        public Long getHoldingId() {
            return holdingId;
        }

        /**
         * Update the balance change
         *
         * @param amount Change amount
         */
        private void updateChange(long amount) {
            change += amount;
        }

        /**
         * Return the balance change
         *
         * @return Balance changes
         */
        public long getChange() {
            return change;
        }

        /**
         * Return the new balance
         *
         * @return New balance
         */
        public long getBalance() {
            return balance;
        }

        /**
         * Set the new balance
         *
         * @param balance New balance
         */
        private void setBalance(long balance) {
            this.balance = balance;
        }

        /**
         * Return the block identifier
         *
         * @return EcBlock identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Return the height
         *
         * @return Height
         */
        public int getHeight() {
            return height;
        }

        /**
         * Return the timestamp
         *
         * @return Timestamp
         */
        public int getTimestamp() {
            return timestamp;
        }

        /**
         * Return the hash code
         *
         * @return Hash code
         */
        @Override
        public int hashCode() {
            return (Long.hashCode(accountId) ^ event.getCode() ^ Long.hashCode(eventId) ^
                    (holding != null ? holding.getCode() : 0) ^ (holdingId != null ? Long.hashCode(holdingId) : 0));
        }

        /**
         * Check if two ledger events are equal
         *
         * @param obj Ledger event to check
         * @return TRUE if the ledger events are the same
         */
        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof LedgerEntry) && accountId == ((LedgerEntry) obj).accountId &&
                    event == ((LedgerEntry) obj).event && eventId == ((LedgerEntry) obj).eventId &&
                    holding == ((LedgerEntry) obj).holding &&
                    (holdingId != null ? holdingId.equals(((LedgerEntry) obj).holdingId) : ((LedgerEntry) obj).holdingId == null));
        }

        /**
         * Save the ledger entry
         *
         * @param con Database connection
         * @throws SQLException Database error occurred
         */
        private void save(Connection con) throws SQLException {
            try (PreparedStatement stmt = con.prepareStatement("INSERT INTO account_ledger "
                    + "(account_id, event_type, event_id, holding_type, holding_id, change, balance, "
                    + "block_id, height, timestamp) "
                    + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                int i = 0;
                stmt.setLong(++i, accountId);
                stmt.setByte(++i, (byte) event.getCode());
                stmt.setLong(++i, eventId);
                if (holding != null) {
                    stmt.setByte(++i, (byte) holding.getCode());
                } else {
                    stmt.setByte(++i, (byte) -1);
                }
                H2Utils.h2setLong(stmt, ++i, holdingId);
                stmt.setLong(++i, change);
                stmt.setLong(++i, balance);
                stmt.setLong(++i, blockId);
                stmt.setInt(++i, height);
                stmt.setInt(++i, timestamp);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        ledgerId = rs.getLong(1);
                    }
                }
            }
        }
    }
}
