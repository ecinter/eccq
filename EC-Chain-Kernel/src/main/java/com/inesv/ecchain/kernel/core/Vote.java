package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Vote {

    private static final H2KeyLongKeyFactory<Vote> VOTE_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Vote>("Id") {
        @Override
        public H2Key newKey(Vote vote) {
            return vote.h2Key;
        }
    };

    private static final EntityH2Table<Vote> VOTE_TABLE = new EntityH2Table<Vote>("vote", VOTE_DB_KEY_FACTORY) {

        @Override
        protected Vote load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Vote(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Vote vote) throws SQLException {
            vote.save(con);
        }

        @Override
        public void trim(int height) {
            super.trim(height);
            try (Connection con = H2.H2.getConnection();
                 H2Iterator<Poll> polls = Poll.getPollsFinishingAtOrBefore(height, 0, Integer.MAX_VALUE);
                 PreparedStatement pstmt = con.prepareStatement("DELETE FROM vote WHERE poll_id = ?")) {
                for (Poll poll : polls) {
                    pstmt.setLong(1, poll.getId());
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    };
    private final long id;
    private final H2Key h2Key;
    private final long pollId;
    private final long voterId;
    private final byte[] voteBytes;

    private Vote(Transaction transaction, Mortgaged.MessagingVoteCasting attachment) {
        this.id = transaction.getTransactionId();
        this.h2Key = VOTE_DB_KEY_FACTORY.newKey(this.id);
        this.pollId = attachment.getPollId();
        this.voterId = transaction.getSenderId();
        this.voteBytes = attachment.getPollVote();
    }

    private Vote(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.pollId = rs.getLong("poll_id");
        this.voterId = rs.getLong("voter_id");
        this.voteBytes = rs.getBytes("vote_bytes");
    }

    public static int getCount() {
        return VOTE_TABLE.getCount();
    }

    public static H2Iterator<Vote> getVotes(long pollId, int from, int to) {
        return VOTE_TABLE.getManyBy(new H2ClauseLongClause("poll_id", pollId), from, to);
    }

    public static Vote getVote(long pollId, long voterId) {
        H2Clause clause = new H2ClauseLongClause("poll_id", pollId).and(new H2ClauseLongClause("voter_id", voterId));
        return VOTE_TABLE.getBy(clause);
    }

    static Vote addVote(Transaction transaction, Mortgaged.MessagingVoteCasting attachment) {
        Vote vote = new Vote(transaction, attachment);
        VOTE_TABLE.insert(vote);
        return vote;
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO vote (id, poll_id, voter_id, "
                + "vote_bytes, height) VALUES (?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.pollId);
            pstmt.setLong(++i, this.voterId);
            pstmt.setBytes(++i, this.voteBytes);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getVoterId() {
        return voterId;
    }

    public byte[] getVoteBytes() {
        return voteBytes;
    }

}
