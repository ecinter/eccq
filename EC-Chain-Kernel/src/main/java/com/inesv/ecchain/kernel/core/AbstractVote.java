package com.inesv.ecchain.kernel.core;

import java.sql.ResultSet;
import java.sql.SQLException;

abstract class AbstractVote {

    final long Id;
    final VoteWeighting voteWeighting;
    final long accountId;
    final int finishHeight;

    AbstractVote(long id, long accountId, int finishHeight, VoteWeighting voteWeighting) {
        this.Id = id;
        this.accountId = accountId;
        this.finishHeight = finishHeight;
        this.voteWeighting = voteWeighting;
    }

    AbstractVote(ResultSet rs) throws SQLException {
        this.Id = rs.getLong("Id");
        this.accountId = rs.getLong("account_id");
        this.finishHeight = rs.getInt("finish_height");
        this.voteWeighting = new VoteWeighting(rs.getByte("voting_model"), rs.getLong("holding_id"),
                rs.getLong("min_balance"), rs.getByte("min_balance_model"));
    }

    public final long getId() {
        return Id;
    }

    public final long getAccountId() {
        return accountId;
    }

    public final int getFinishHeight() {
        return finishHeight;
    }

    public final VoteWeighting getVoteWeighting() {
        return voteWeighting;
    }

}

