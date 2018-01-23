package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PhasingVote {

    private static final H2KeyLinkKeyFactory<PhasingVote> PHASING_VOTE_DB_KEY_FACTORY = new H2KeyLinkKeyFactory<PhasingVote>("transaction_id", "voter_id") {
        @Override
        public H2Key newKey(PhasingVote vote) {
            return vote.h2Key;
        }
    };

    private static final EntityH2Table<PhasingVote> PHASING_VOTE_TABLE = new EntityH2Table<PhasingVote>("phasing_vote", PHASING_VOTE_DB_KEY_FACTORY) {

        @Override
        protected PhasingVote load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PhasingVote(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PhasingVote vote) throws SQLException {
            vote.save(con);
        }

    };
    private final long phasedTransactionId;
    private final long voterId;
    private final H2Key h2Key;
    private long voteId;

    private PhasingVote(Transaction transaction, Account voter, long phasedTransactionId) {
        this.phasedTransactionId = phasedTransactionId;
        this.voterId = voter.getId();
        this.h2Key = PHASING_VOTE_DB_KEY_FACTORY.newKey(this.phasedTransactionId, this.voterId);
        this.voteId = transaction.getTransactionId();
    }

    private PhasingVote(ResultSet rs, H2Key h2Key) throws SQLException {
        this.phasedTransactionId = rs.getLong("transaction_id");
        this.voterId = rs.getLong("voter_id");
        this.h2Key = h2Key;
        this.voteId = rs.getLong("vote_id");
    }

    public static H2Iterator<PhasingVote> getVotes(long phasedTransactionId, int from, int to) {
        return PHASING_VOTE_TABLE.getManyBy(new H2ClauseLongClause("transaction_id", phasedTransactionId), from, to);
    }

    public static PhasingVote getVote(long phasedTransactionId, long voterId) {
        return PHASING_VOTE_TABLE.get(PHASING_VOTE_DB_KEY_FACTORY.newKey(phasedTransactionId, voterId));
    }

    public static long getVoteCount(long phasedTransactionId) {
        return PHASING_VOTE_TABLE.getCount(new H2ClauseLongClause("transaction_id", phasedTransactionId));
    }

    static void addVote(Transaction transaction, Account voter, long phasedTransactionId) {
        PhasingVote phasingVote = PHASING_VOTE_TABLE.get(PHASING_VOTE_DB_KEY_FACTORY.newKey(phasedTransactionId, voter.getId()));
        if (phasingVote == null) {
            phasingVote = new PhasingVote(transaction, voter, phasedTransactionId);
            PHASING_VOTE_TABLE.insert(phasingVote);
        }
    }

    public static void start() {
    }

    public long getVoterId() {
        return voterId;
    }

    public long getVoteId() {
        return voteId;
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO phasing_vote (vote_id, transaction_id, "
                + "voter_id, height) VALUES (?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.voteId);
            pstmt.setLong(++i, this.phasedTransactionId);
            pstmt.setLong(++i, this.voterId);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

}
