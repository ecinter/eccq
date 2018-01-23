package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.kernel.H2.*;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

public final class Poll extends AbstractVote {

    private static final boolean IS_POLLS_PROCESSING = PropertiesUtil.getKeyForBoolean("ec.processPolls");
    private static final H2KeyLongKeyFactory<Poll> POLL_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Poll>("Id") {
        @Override
        public H2Key newKey(Poll poll) {
            return poll.h2Key == null ? newKey(poll.Id) : poll.h2Key;
        }
    };
    private final static EntityH2Table<Poll> POLL_TABLE = new EntityH2Table<Poll>("poll", POLL_DB_KEY_FACTORY, "name,description") {

        @Override
        protected Poll load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Poll(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Poll poll) throws SQLException {
            poll.save(con);
        }
    };
    private static final H2KeyLongKeyFactory<Poll> POLL_RESULTS_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Poll>("poll_id") {
        @Override
        public H2Key newKey(Poll poll) {
            return poll.h2Key;
        }
    };
    private static final ValuesH2Table<Poll, OptionResult> POLL_RESULTS_TABLE = new ValuesH2Table<Poll, OptionResult>("poll_result", POLL_RESULTS_DB_KEY_FACTORY) {

        @Override
        protected OptionResult load(Connection con, ResultSet rs) throws SQLException {
            long weight = rs.getLong("weight");
            return weight == 0 ? null : new OptionResult(rs.getLong("result"), weight);
        }

        @Override
        protected void save(Connection con, Poll poll, OptionResult optionResult) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO poll_result (poll_id, "
                    + "result, weight, height) VALUES (?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, poll.getId());
                if (optionResult != null) {
                    pstmt.setLong(++i, optionResult.result);
                    pstmt.setLong(++i, optionResult.weight);
                } else {
                    pstmt.setNull(++i, Types.BIGINT);
                    pstmt.setLong(++i, 0);
                }
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }
    };
    @PostConstruct
    public static void initPostConstruct()
     {
        if (Poll.IS_POLLS_PROCESSING) {
            EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
                int height = block.getHeight();
                if (height >= Constants.EC_PHASING_BLOCK) {
                    Poll.checkPolls(height);
                }
            }, EcBlockchainProcessorEvent.AFTER_BLOCK_APPLY);
        }
    }

    private final H2Key h2Key;
    private final String name;
    private final String description;
    private final String[] options;
    private final byte minNumberOfOptions;
    private final byte maxNumberOfOptions;
    private final byte minRangeValue;
    private final byte maxRangeValue;
    private final int timestamp;

    private Poll(Transaction transaction, Mortgaged.MessagingPollCreation attachment) {
        super(transaction.getTransactionId(), transaction.getSenderId(), attachment.getFinishHeight(), attachment.getVoteWeighting());
        this.h2Key = POLL_DB_KEY_FACTORY.newKey(this.Id);
        this.name = attachment.getPollName();
        this.description = attachment.getPollDescription();
        this.options = attachment.getPollOptions();
        this.minNumberOfOptions = attachment.getMinNumberOfOptions();
        this.maxNumberOfOptions = attachment.getMaxNumberOfOptions();
        this.minRangeValue = attachment.getMinRangeValue();
        this.maxRangeValue = attachment.getMaxRangeValue();
        this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
    }

    private Poll(ResultSet rs, H2Key h2Key) throws SQLException {
        super(rs);
        this.h2Key = h2Key;
        this.name = rs.getString("name");
        this.description = rs.getString("description");
        this.options = H2Utils.h2getArray(rs, "options", String[].class);
        this.minNumberOfOptions = rs.getByte("min_num_options");
        this.maxNumberOfOptions = rs.getByte("max_num_options");
        this.minRangeValue = rs.getByte("min_range_value");
        this.maxRangeValue = rs.getByte("max_range_value");
        this.timestamp = rs.getInt("timestamp");
    }

    public static Poll getPoll(long id) {
        return POLL_TABLE.get(POLL_DB_KEY_FACTORY.newKey(id));
    }

    public static H2Iterator<Poll> getPollsFinishingAtOrBefore(int height, int from, int to) {
        return POLL_TABLE.getManyBy(new H2ClauseIntClause("finish_height", H2ClauseOp.LTE, height), from, to);
    }

    public static H2Iterator<Poll> getAllPolls(int from, int to) {
        return POLL_TABLE.getAll(from, to);
    }

    public static H2Iterator<Poll> getActivePolls(int from, int to) {
        return POLL_TABLE.getManyBy(new H2ClauseIntClause("finish_height", H2ClauseOp.GT, EcBlockchainImpl.getInstance().getHeight()), from, to);
    }

    public static H2Iterator<Poll> getPollsByAccount(long accountId, boolean includeFinished, boolean finishedOnly, int from, int to) {
        H2Clause h2Clause = new H2ClauseLongClause("account_id", accountId);
        if (finishedOnly) {
            h2Clause = h2Clause.and(new H2ClauseIntClause("finish_height", H2ClauseOp.LTE, EcBlockchainImpl.getInstance().getHeight()));
        } else if (!includeFinished) {
            h2Clause = h2Clause.and(new H2ClauseIntClause("finish_height", H2ClauseOp.GT, EcBlockchainImpl.getInstance().getHeight()));
        }
        return POLL_TABLE.getManyBy(h2Clause, from, to);
    }

    public static H2Iterator<Poll> getPollsFinishingAt(int height) {
        return POLL_TABLE.getManyBy(new H2ClauseIntClause("finish_height", height), 0, Integer.MAX_VALUE);
    }

    public static H2Iterator<Poll> searchPolls(String query, boolean includeFinished, int from, int to) {
        H2Clause h2Clause = includeFinished ? H2Clause.EMPTY_CLAUSE : new H2ClauseIntClause("finish_height", H2ClauseOp.GT, EcBlockchainImpl.getInstance().getHeight());
        return POLL_TABLE.search(query, h2Clause, from, to, " ORDER BY ft.score DESC, poll.height DESC, poll.db_id DESC ");
    }

    public static int getCount() {
        return POLL_TABLE.getCount();
    }

    static void addPoll(Transaction transaction, Mortgaged.MessagingPollCreation attachment) {
        Poll poll = new Poll(transaction, attachment);
        POLL_TABLE.insert(poll);
    }

    public static void start() {
    }

    private static void checkPolls(int currentHeight) {
        try (H2Iterator<Poll> polls = getPollsFinishingAt(currentHeight)) {
            for (Poll poll : polls) {
                try {
                    List<OptionResult> results = poll.countResults(poll.getVoteWeighting(), currentHeight);
                    POLL_RESULTS_TABLE.insert(poll, results);
                    LoggerUtil.logDebug("Poll " + Long.toUnsignedString(poll.getId()) + " has been finished");
                } catch (RuntimeException e) {
                    LoggerUtil.logError("Couldn't count votes for poll " + Long.toUnsignedString(poll.getId()));
                }
            }
        }
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO poll (id, account_id, "
                + "name, description, options, finish_height, voting_model, min_balance, min_balance_model, "
                + "holding_id, min_num_options, max_num_options, min_range_value, max_range_value, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, Id);
            pstmt.setLong(++i, accountId);
            pstmt.setString(++i, name);
            pstmt.setString(++i, description);
            H2Utils.h2setArray(pstmt, ++i, options);
            pstmt.setInt(++i, finishHeight);
            pstmt.setByte(++i, voteWeighting.getVotingModel().getCode());
            H2Utils.h2setLongZeroToNull(pstmt, ++i, voteWeighting.getMinBalance());
            pstmt.setByte(++i, voteWeighting.getMinBalanceModel().getCode());
            H2Utils.h2setLongZeroToNull(pstmt, ++i, voteWeighting.getHoldingId());
            pstmt.setByte(++i, minNumberOfOptions);
            pstmt.setByte(++i, maxNumberOfOptions);
            pstmt.setByte(++i, minRangeValue);
            pstmt.setByte(++i, maxRangeValue);
            pstmt.setInt(++i, timestamp);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public List<OptionResult> getResults(VoteWeighting voteWeighting) {
        if (this.voteWeighting.equals(voteWeighting)) {
            return getResults();
        } else {
            return countResults(voteWeighting);
        }

    }

    public List<OptionResult> getResults() {
        if (Poll.IS_POLLS_PROCESSING && isFinished()) {
            return POLL_RESULTS_TABLE.get(POLL_DB_KEY_FACTORY.newKey(this));
        } else {
            return countResults(voteWeighting);
        }
    }

    public H2Iterator<Vote> getVotes() {
        return Vote.getVotes(this.getId(), 0, -1);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getOptions() {
        return options;
    }

    public byte getMinNumberOfOptions() {
        return minNumberOfOptions;
    }

    public byte getMaxNumberOfOptions() {
        return maxNumberOfOptions;
    }

    public byte getMinRangeValue() {
        return minRangeValue;
    }

    public byte getMaxRangeValue() {
        return maxRangeValue;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public boolean isFinished() {
        return finishHeight <= EcBlockchainImpl.getInstance().getHeight();
    }

    private List<OptionResult> countResults(VoteWeighting voteWeighting) {
        int countHeight = Math.min(finishHeight, EcBlockchainImpl.getInstance().getHeight());
        if (countHeight < EcBlockchainProcessorImpl.getInstance().getMinRollbackHeight()) {
            return null;
        }
        return countResults(voteWeighting, countHeight);
    }

    private List<OptionResult> countResults(VoteWeighting voteWeighting, int height) {
        final OptionResult[] result = new OptionResult[options.length];
        VoteWeighting.VotingModel votingModel = voteWeighting.getVotingModel();
        try (H2Iterator<Vote> votes = Vote.getVotes(this.getId(), 0, -1)) {
            for (Vote vote : votes) {
                long weight = votingModel.calcWeight(voteWeighting, vote.getVoterId(), height);
                if (weight <= 0) {
                    continue;
                }
                long[] partialResult = countVote(vote, weight);
                for (int i = 0; i < partialResult.length; i++) {
                    if (partialResult[i] != Long.MIN_VALUE) {
                        if (result[i] == null) {
                            result[i] = new OptionResult(partialResult[i], weight);
                        } else {
                            result[i].add(partialResult[i], weight);
                        }
                    }
                }
            }
        }
        return Arrays.asList(result);
    }

    private long[] countVote(Vote vote, long weight) {
        final long[] partialResult = new long[options.length];
        final byte[] optionValues = vote.getVoteBytes();
        for (int i = 0; i < optionValues.length; i++) {
            if (optionValues[i] != Constants.EC_NO_VOTE_VALUE) {
                partialResult[i] = (long) optionValues[i] * weight;
            } else {
                partialResult[i] = Long.MIN_VALUE;
            }
        }
        return partialResult;
    }

    public static final class OptionResult {

        private long result;
        private long weight;

        private OptionResult(long result, long weight) {
            this.result = result;
            this.weight = weight;
        }

        public long getResult() {
            return result;
        }

        public long getWeight() {
            return weight;
        }

        private void add(long vote, long weight) {
            this.result += vote;
            this.weight += weight;
        }

    }

}