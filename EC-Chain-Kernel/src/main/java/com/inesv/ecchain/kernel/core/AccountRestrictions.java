package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcAccountControlExceptionEcEc;
import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public final class AccountRestrictions {

    private static final H2KeyLongKeyFactory<PhasingOnly> PHASING_CONTROL_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PhasingOnly>("account_id") {
        @Override
        public H2Key newKey(PhasingOnly rule) {
            return rule.h2Key;
        }
    };
    private static final VersionedEntityH2Table<PhasingOnly> PHASING_CONTROL_TABLE = new VersionedEntityH2Table<PhasingOnly>("account_control_phasing", PHASING_CONTROL_DB_KEY_FACTORY) {

        @Override
        protected PhasingOnly load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PhasingOnly(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PhasingOnly phasingOnly) throws SQLException {
            phasingOnly.save(con);
        }
    };

    public static void start() {
    }

    static void checkTransaction(Transaction transaction, boolean validatingAtFinish) throws EcNotCurrentlyValidExceptionEc {
        Account senderAccount = Account.getAccount(transaction.getSenderId());
        if (senderAccount == null) {
            throw new EcNotCurrentlyValidExceptionEc("Account " + Long.toUnsignedString(transaction.getSenderId()) + " does not exist yet");
        }
        if (senderAccount.getControls().contains(ControlType.PHASING_ONLY)) {
            PhasingOnly phasingOnly = PhasingOnly.get(transaction.getSenderId());
            phasingOnly.checkTransaction(transaction, validatingAtFinish);
        }
    }

    static boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        Account senderAccount = Account.getAccount(transaction.getSenderId());
        if (!senderAccount.getControls().contains(ControlType.PHASING_ONLY)) {
            return false;
        }
        if (PhasingOnly.get(transaction.getSenderId()).getMaxFees() == 0) {
            return false;
        }
        return transaction.getTransactionType() != AccountControl.SET_PHASING_ONLY &&
                TransactionType.isDuplicate(AccountControl.SET_PHASING_ONLY, Long.toUnsignedString(senderAccount.getId()),
                        duplicates, true);
    }

    public static final class PhasingOnly {

        private final H2Key h2Key;
        private final long accountId;
        private PhasingParams phasingParams;
        private long maxFees;
        private short minDuration;
        private short maxDuration;
        private PhasingOnly(long accountId, PhasingParams params, long maxFees, short minDuration, short maxDuration) {
            this.accountId = accountId;
            h2Key = PHASING_CONTROL_DB_KEY_FACTORY.newKey(this.accountId);
            phasingParams = params;
            this.maxFees = maxFees;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
        }
        private PhasingOnly(ResultSet rs, H2Key h2Key) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.h2Key = h2Key;
            Long[] whitelist = H2Utils.h2getArray(rs, "whitelist", Long[].class);
            phasingParams = new PhasingParams(rs.getByte("voting_model"),
                    rs.getLong("holding_id"),
                    rs.getLong("quorum"),
                    rs.getLong("min_balance"),
                    rs.getByte("min_balance_model"),
                    whitelist == null ? Convert.EC_EMPTY_LONG : Convert.toArray(whitelist));
            this.maxFees = rs.getLong("max_fees");
            this.minDuration = rs.getShort("min_duration");
            this.maxDuration = rs.getShort("max_duration");
        }

        public static PhasingOnly get(long accountId) {
            return PHASING_CONTROL_TABLE.get(PHASING_CONTROL_DB_KEY_FACTORY.newKey(accountId));
        }

        public static int getCount() {
            return PHASING_CONTROL_TABLE.getCount();
        }

        public static H2Iterator<PhasingOnly> getAll(int from, int to) {
            return PHASING_CONTROL_TABLE.getAll(from, to);
        }

        static void set(Account senderAccount, Mortgaged.SetPhasingOnly attachment) {
            PhasingParams phasingParams = attachment.getPhasingParams();
            if (phasingParams.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.NONE) {
                //no voting - del the control
                senderAccount.removeControl(ControlType.PHASING_ONLY);
                PhasingOnly phasingOnly = get(senderAccount.getId());
                PHASING_CONTROL_TABLE.delete(phasingOnly);
            } else {
                senderAccount.addControl(ControlType.PHASING_ONLY);
                PhasingOnly phasingOnly = get(senderAccount.getId());
                if (phasingOnly == null) {
                    phasingOnly = new PhasingOnly(senderAccount.getId(), phasingParams, attachment.getMaxFees(),
                            attachment.getMinDuration(), attachment.getMaxDuration());
                } else {
                    phasingOnly.phasingParams = phasingParams;
                    phasingOnly.maxFees = attachment.getMaxFees();
                    phasingOnly.minDuration = attachment.getMinDuration();
                    phasingOnly.maxDuration = attachment.getMaxDuration();
                }
                PHASING_CONTROL_TABLE.insert(phasingOnly);
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public PhasingParams getPhasingParams() {
            return phasingParams;
        }

        public long getMaxFees() {
            return maxFees;
        }

        public short getMinDuration() {
            return minDuration;
        }

        public short getMaxDuration() {
            return maxDuration;
        }

        private void checkTransaction(Transaction transaction, boolean validatingAtFinish) throws EcAccountControlExceptionEcEc {
            if (!validatingAtFinish && maxFees > 0 && Math.addExact(transaction.getFeeNQT(), PhasingPoll.getSenderPhasedTransactionFees(transaction.getSenderId())) > maxFees) {
                throw new EcAccountControlExceptionEcEc(String.format("Maximum total fees limit of %f EC exceeded", ((double) maxFees) / Constants.ONE_EC));
            }
            if (transaction.getTransactionType() == Messaging.PHASING_VOTE_CASTING) {
                return;
            }
            try {
                phasingParams.checkApprovable();
            } catch (EcNotCurrentlyValidExceptionEc e) {
                LoggerUtil.logDebug("Account control no longer valid: " + e.getMessage());
                return;
            }
            Phasing phasingAppendix = transaction.getPhasing();
            if (phasingAppendix == null) {
                throw new EcAccountControlExceptionEcEc("Non-phased transaction when phasing account control is enabled");
            }
            if (!phasingParams.equals(phasingAppendix.getParams())) {
                throw new EcAccountControlExceptionEcEc("Phasing parameters mismatch phasing account control. Expected: " +
                        phasingParams.toString() + " . Actual: " + phasingAppendix.getParams().toString());
            }
            if (!validatingAtFinish) {
                int duration = phasingAppendix.getFinishHeight() - EcBlockchainImpl.getInstance().getHeight();
                if ((maxDuration > 0 && duration > maxDuration) || (minDuration > 0 && duration < minDuration)) {
                    throw new EcAccountControlExceptionEcEc("Invalid phasing duration " + duration);
                }
            }
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO account_control_phasing "
                    + "(account_id, whitelist, voting_model, quorum, min_balance, holding_id, min_balance_model, "
                    + "max_fees, min_duration, max_duration, height, latest) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                H2Utils.h2setArrayEmptyToNull(pstmt, ++i, Convert.toArray(phasingParams.getWhitelist()));
                pstmt.setByte(++i, phasingParams.getVoteWeighting().getVotingModel().getCode());
                H2Utils.h2setLongZeroToNull(pstmt, ++i, phasingParams.getQuorum());
                H2Utils.h2setLongZeroToNull(pstmt, ++i, phasingParams.getVoteWeighting().getMinBalance());
                H2Utils.h2setLongZeroToNull(pstmt, ++i, phasingParams.getVoteWeighting().getHoldingId());
                pstmt.setByte(++i, phasingParams.getVoteWeighting().getMinBalanceModel().getCode());
                pstmt.setLong(++i, this.maxFees);
                pstmt.setShort(++i, this.minDuration);
                pstmt.setShort(++i, this.maxDuration);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

    }

}
