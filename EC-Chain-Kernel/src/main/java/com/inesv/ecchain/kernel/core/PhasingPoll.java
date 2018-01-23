package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.crypto.HashFunction;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class PhasingPoll extends AbstractVote {

    public static final Set<HashFunction> ACCEPTED_HASH_FUNCTIONS =
            Collections.unmodifiableSet(EnumSet.of(HashFunction.SHA256, HashFunction.RIPEMD160, HashFunction.RIPEMD160_SHA256));
    private static final H2KeyLongKeyFactory<PhasingPoll> PHASING_POLL_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PhasingPoll>("Id") {
        @Override
        public H2Key newKey(PhasingPoll poll) {
            return poll.h2Key;
        }
    };
    private static final EntityH2Table<PhasingPoll> PHASING_POLL_TABLE = new EntityH2Table<PhasingPoll>("phasing_poll", PHASING_POLL_DB_KEY_FACTORY) {

        @Override
        protected PhasingPoll load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PhasingPoll(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PhasingPoll poll) throws SQLException {
            poll.save(con);
        }

        @Override
        public void trim(int height) {
            super.trim(height);
            try (Connection con = H2.H2.getConnection();
                 H2Iterator<PhasingPoll> pollsToTrim = PHASING_POLL_TABLE.getManyBy(new H2ClauseIntClause("finish_height", H2ClauseOp.LT, height), 0, -1);
                 PreparedStatement pstmt1 = con.prepareStatement("DELETE FROM phasing_poll WHERE id = ?");
                 PreparedStatement pstmt2 = con.prepareStatement("DELETE FROM phasing_poll_voter WHERE transaction_id = ?");
                 PreparedStatement pstmt3 = con.prepareStatement("DELETE FROM phasing_vote WHERE transaction_id = ?");
                 PreparedStatement pstmt4 = con.prepareStatement("DELETE FROM phasing_poll_linked_transaction WHERE transaction_id = ?")) {
                while (pollsToTrim.hasNext()) {
                    long id = pollsToTrim.next().getId();
                    pstmt1.setLong(1, id);
                    pstmt1.executeUpdate();
                    pstmt2.setLong(1, id);
                    pstmt2.executeUpdate();
                    pstmt3.setLong(1, id);
                    pstmt3.executeUpdate();
                    pstmt4.setLong(1, id);
                    pstmt4.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    };
    private static final H2KeyLongKeyFactory<PhasingPoll> VOTERS_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PhasingPoll>("transaction_id") {
        @Override
        public H2Key newKey(PhasingPoll poll) {
            return poll.h2Key == null ? newKey(poll.Id) : poll.h2Key;
        }
    };
    private static final ValuesH2Table<PhasingPoll, Long> VOTERS_TABLE = new ValuesH2Table<PhasingPoll, Long>("phasing_poll_voter", VOTERS_DB_KEY_FACTORY) {

        @Override
        protected Long load(Connection con, ResultSet rs) throws SQLException {
            return rs.getLong("voter_id");
        }

        @Override
        protected void save(Connection con, PhasingPoll poll, Long accountId) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll_voter (transaction_id, "
                    + "voter_id, height) VALUES (?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, poll.getId());
                pstmt.setLong(++i, accountId);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }
    };
    private static final H2KeyLongKeyFactory<PhasingPoll> LINKED_TRANSACTION_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PhasingPoll>("transaction_id") {
        @Override
        public H2Key newKey(PhasingPoll poll) {
            return poll.h2Key == null ? newKey(poll.Id) : poll.h2Key;
        }
    };
    private static final ValuesH2Table<PhasingPoll, byte[]> LINKED_TRANSACTION_TABLE = new ValuesH2Table<PhasingPoll, byte[]>("phasing_poll_linked_transaction",
            LINKED_TRANSACTION_DB_KEY_FACTORY) {

        @Override
        protected byte[] load(Connection con, ResultSet rs) throws SQLException {
            return rs.getBytes("linked_full_hash");
        }

        @Override
        protected void save(Connection con, PhasingPoll poll, byte[] linkedFullHash) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll_linked_transaction (transaction_id, "
                    + "linked_full_hash, linked_transaction_id, height) VALUES (?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, poll.getId());
                pstmt.setBytes(++i, linkedFullHash);
                pstmt.setLong(++i, Convert.fullhashtoid(linkedFullHash));
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }
    };
    private static final H2KeyLongKeyFactory<PhasingPollResult> RESULT_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PhasingPollResult>("Id") {
        @Override
        public H2Key newKey(PhasingPollResult phasingPollResult) {
            return phasingPollResult.h2Key;
        }
    };
    private static final EntityH2Table<PhasingPollResult> RESULT_TABLE = new EntityH2Table<PhasingPollResult>("phasing_poll_result", RESULT_DB_KEY_FACTORY) {

        @Override
        protected PhasingPollResult load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PhasingPollResult(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PhasingPollResult phasingPollResult) throws SQLException {
            phasingPollResult.save(con);
        }
    };
    private final H2Key h2Key;
    private final long[] whitelist;
    private final long quorum;
    private final byte[] hashedSecret;
    private final byte algorithm;

    private PhasingPoll(Transaction transaction, Phasing appendix) {
        super(transaction.getTransactionId(), transaction.getSenderId(), appendix.getFinishHeight(), appendix.getVoteWeighting());
        this.h2Key = PHASING_POLL_DB_KEY_FACTORY.newKey(this.Id);
        this.quorum = appendix.getQuorum();
        this.whitelist = appendix.getWhitelist();
        this.hashedSecret = appendix.getHashedSecret();
        this.algorithm = appendix.getAlgorithm();
    }

    private PhasingPoll(ResultSet rs, H2Key h2Key) throws SQLException {
        super(rs);
        this.h2Key = h2Key;
        this.quorum = rs.getLong("quorum");
        this.whitelist = rs.getByte("whitelist_size") == 0 ? Convert.EC_EMPTY_LONG : Convert.toArray(VOTERS_TABLE.get(VOTERS_DB_KEY_FACTORY.newKey(this)));
        hashedSecret = rs.getBytes("hashed_secret");
        algorithm = rs.getByte("algorithm");
    }

    public static HashFunction getHashFunction(byte code) {
        try {
            HashFunction hashFunction = HashFunction.getHashFunction(code);
            if (ACCEPTED_HASH_FUNCTIONS.contains(hashFunction)) {
                return hashFunction;
            }
        } catch (IllegalArgumentException ignore) {
        }
        return null;
    }

    public static PhasingPollResult getResult(long id) {
        return RESULT_TABLE.get(RESULT_DB_KEY_FACTORY.newKey(id));
    }

    public static H2Iterator<PhasingPollResult> getApproved(int height) {
        return RESULT_TABLE.getManyBy(new H2ClauseIntClause("height", height).and(new H2ClauseBooleanClause("approved", true)),
                0, -1, " ORDER BY db_id ASC ");
    }

    public static PhasingPoll getPoll(long id) {
        return PHASING_POLL_TABLE.get(PHASING_POLL_DB_KEY_FACTORY.newKey(id));
    }

    static H2Iterator<TransactionImpl> getFinishingTransactions(int height) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* FROM transaction, phasing_poll " +
                    "WHERE phasing_poll.id = transaction.id AND phasing_poll.finish_height = ? " +
                    "ORDER BY transaction.height, transaction.transaction_index"); // ASC, not DESC
            pstmt.setInt(1, height);
            return EcBlockchainImpl.getInstance().getTransactions(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<TransactionImpl> getVoterPhasedTransactions(long voterId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* "
                    + "FROM transaction, phasing_poll_voter, phasing_poll "
                    + "LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id "
                    + "WHERE transaction.id = phasing_poll.id AND "
                    + "phasing_poll.finish_height > ? AND "
                    + "phasing_poll.id = phasing_poll_voter.transaction_id "
                    + "AND phasing_poll_voter.voter_id = ? "
                    + "AND phasing_poll_result.id IS NULL "
                    + "ORDER BY transaction.height DESC, transaction.transaction_index DESC "
                    + H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.setLong(++i, voterId);
            H2Utils.setLimits(++i, pstmt, from, to);

            return EcBlockchainImpl.getInstance().getTransactions(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<TransactionImpl> getHoldingPhasedTransactions(long holdingId, VoteWeighting.VotingModel votingModel,
                                                                           long accountId, boolean withoutWhitelist, int from, int to) {

        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* " +
                    "FROM transaction, phasing_poll " +
                    "WHERE phasing_poll.holding_id = ? " +
                    "AND phasing_poll.voting_model = ? " +
                    "AND phasing_poll.id = transaction.id " +
                    "AND phasing_poll.finish_height > ? " +
                    (accountId != 0 ? "AND phasing_poll.account_id = ? " : "") +
                    (withoutWhitelist ? "AND phasing_poll.whitelist_size = 0 " : "") +
                    "ORDER BY transaction.height DESC, transaction.transaction_index DESC " +
                    H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, holdingId);
            pstmt.setByte(++i, votingModel.getCode());
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            if (accountId != 0) {
                pstmt.setLong(++i, accountId);
            }
            H2Utils.setLimits(++i, pstmt, from, to);

            return EcBlockchainImpl.getInstance().getTransactions(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static H2Iterator<TransactionImpl> getAccountPhasedTransactions(long accountId, int from, int to) {
        Connection con = null;
        try {
            con = H2.H2.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* FROM transaction, phasing_poll " +
                    " LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id " +
                    " WHERE phasing_poll.id = transaction.id AND (transaction.sender_id = ? OR transaction.recipient_id = ?) " +
                    " AND phasing_poll_result.id IS NULL " +
                    " AND phasing_poll.finish_height > ? ORDER BY transaction.height DESC, transaction.transaction_index DESC " +
                    H2Utils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            H2Utils.setLimits(++i, pstmt, from, to);

            return EcBlockchainImpl.getInstance().getTransactions(con, pstmt);
        } catch (SQLException e) {
            H2Utils.h2close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static int getAccountPhasedTransactionCount(long accountId) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM transaction, phasing_poll " +
                     " LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id " +
                     " WHERE phasing_poll.id = transaction.id AND (transaction.sender_id = ? OR transaction.recipient_id = ?) " +
                     " AND phasing_poll_result.id IS NULL " +
                     " AND phasing_poll.finish_height > ?")) {
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static List<? extends Transaction> getLinkedPhasedTransactions(byte[] linkedTransactionFullHash) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT transaction_id FROM phasing_poll_linked_transaction " +
                     "WHERE linked_transaction_id = ? AND linked_full_hash = ?")) {
            int i = 0;
            pstmt.setLong(++i, Convert.fullhashtoid(linkedTransactionFullHash));
            pstmt.setBytes(++i, linkedTransactionFullHash);
            List<TransactionImpl> transactions = new ArrayList<>();
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(TransactionH2.selectTransaction(rs.getLong("transaction_id")));
                }
            }
            return transactions;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static long getSenderPhasedTransactionFees(long accountId) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT SUM(transaction.fee) AS fees FROM transaction, phasing_poll " +
                     " LEFT JOIN phasing_poll_result ON phasing_poll.id = phasing_poll_result.id " +
                     " WHERE phasing_poll.id = transaction.id AND transaction.sender_id = ? " +
                     " AND phasing_poll_result.id IS NULL " +
                     " AND phasing_poll.finish_height > ?")) {
            int i = 0;
            pstmt.setLong(++i, accountId);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getLong("fees");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void addPoll(Transaction transaction, Phasing appendix) {
        PhasingPoll poll = new PhasingPoll(transaction, appendix);
        PHASING_POLL_TABLE.insert(poll);
        long[] voters = poll.whitelist;
        if (voters.length > 0) {
            VOTERS_TABLE.insert(poll, Convert.toList(voters));
        }
        if (appendix.getLinkedFullHashes().length > 0) {
            List<byte[]> linkedFullHashes = new ArrayList<>(appendix.getLinkedFullHashes().length);
            Collections.addAll(linkedFullHashes, appendix.getLinkedFullHashes());
            LINKED_TRANSACTION_TABLE.insert(poll, linkedFullHashes);
        }
    }

    public static void start() {
    }

    void finish(long result) {
        PhasingPollResult phasingPollResult = new PhasingPollResult(this, result);
        RESULT_TABLE.insert(phasingPollResult);
    }

    public long[] getWhitelist() {
        return whitelist;
    }

    public long getQuorum() {
        return quorum;
    }

    public byte[] getFullHash() {
        return TransactionH2.getFullHash(this.Id);
    }

    public List<byte[]> getLinkedFullHashes() {
        return LINKED_TRANSACTION_TABLE.get(LINKED_TRANSACTION_DB_KEY_FACTORY.newKey(this));
    }

    public byte[] getHashedSecret() {
        return hashedSecret;
    }

    public byte getAlgorithm() {
        return algorithm;
    }

    public boolean verifySecret(byte[] revealedSecret) {
        HashFunction hashFunction = getHashFunction(algorithm);
        return hashFunction != null && Arrays.equals(hashedSecret, hashFunction.hash(revealedSecret));
    }

    public long countVotes() {
        if (voteWeighting.getVotingModel() == VoteWeighting.VotingModel.NONE) {
            return 0;
        }
        int height = Math.min(this.finishHeight, EcBlockchainImpl.getInstance().getHeight());
        if (voteWeighting.getVotingModel() == VoteWeighting.VotingModel.TRANSACTION) {
            int count = 0;
            for (byte[] hash : getLinkedFullHashes()) {
                if (TransactionH2.hasTransactionByFullHash(hash, height)) {
                    count += 1;
                }
            }
            return count;
        }
        if (voteWeighting.isBalanceIndependent()) {
            return PhasingVote.getVoteCount(this.Id);
        }
        VoteWeighting.VotingModel votingModel = voteWeighting.getVotingModel();
        long cumulativeWeight = 0;
        try (H2Iterator<PhasingVote> votes = PhasingVote.getVotes(this.Id, 0, Integer.MAX_VALUE)) {
            for (PhasingVote vote : votes) {
                cumulativeWeight += votingModel.calcWeight(voteWeighting, vote.getVoterId(), height);
            }
        }
        return cumulativeWeight;
    }

    boolean allowEarlyFinish() {
        return voteWeighting.isBalanceIndependent() && (whitelist.length > 0 || voteWeighting.getVotingModel() != VoteWeighting.VotingModel.ACCOUNT);
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll (id, account_id, "
                + "finish_height, whitelist_size, voting_model, quorum, min_balance, holding_id, "
                + "min_balance_model, hashed_secret, algorithm, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, Id);
            pstmt.setLong(++i, accountId);
            pstmt.setInt(++i, finishHeight);
            pstmt.setByte(++i, (byte) whitelist.length);
            pstmt.setByte(++i, voteWeighting.getVotingModel().getCode());
            H2Utils.h2setLongZeroToNull(pstmt, ++i, quorum);
            H2Utils.h2setLongZeroToNull(pstmt, ++i, voteWeighting.getMinBalance());
            H2Utils.h2setLongZeroToNull(pstmt, ++i, voteWeighting.getHoldingId());
            pstmt.setByte(++i, voteWeighting.getMinBalanceModel().getCode());
            H2Utils.h2setBytes(pstmt, ++i, hashedSecret);
            pstmt.setByte(++i, algorithm);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public static final class PhasingPollResult {

        private final long id;
        private final H2Key h2Key;
        private final long result;
        private final boolean approved;
        private final int height;

        private PhasingPollResult(PhasingPoll poll, long result) {
            this.id = poll.getId();
            this.h2Key = RESULT_DB_KEY_FACTORY.newKey(this.id);
            this.result = result;
            this.approved = result >= poll.getQuorum();
            this.height = EcBlockchainImpl.getInstance().getHeight();
        }

        private PhasingPollResult(ResultSet rs, H2Key h2Key) throws SQLException {
            this.id = rs.getLong("Id");
            this.h2Key = h2Key;
            this.result = rs.getLong("result");
            this.approved = rs.getBoolean("approved");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_poll_result (id, "
                    + "result, approved, height) VALUES (?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, id);
                pstmt.setLong(++i, result);
                pstmt.setBoolean(++i, approved);
                pstmt.setInt(++i, height);
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getResult() {
            return result;
        }

        public boolean isApproved() {
            return approved;
        }

        public int getHeight() {
            return height;
        }
    }
}