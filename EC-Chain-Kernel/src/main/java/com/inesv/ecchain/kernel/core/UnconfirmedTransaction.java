package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.H2.H2Key;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.sql.*;
import java.util.List;

class UnconfirmedTransaction implements Transaction {

    private final TransactionImpl transaction;
    private final long arrivalTimestamp;
    private final long feePerByte;

    UnconfirmedTransaction(TransactionImpl transaction, long arrivalTimestamp) {
        this.transaction = transaction;
        this.arrivalTimestamp = arrivalTimestamp;
        this.feePerByte = transaction.getFeeNQT() / transaction.getFullSize();
    }

    UnconfirmedTransaction(ResultSet rs) throws SQLException {
        try {
            byte[] transactionBytes = rs.getBytes("transaction_bytes");
            JSONObject prunableAttachments = null;
            String prunableJSON = rs.getString("prunable_json");
            if (prunableJSON != null) {
                prunableAttachments = (JSONObject) JSONValue.parse(prunableJSON);
            }
            TransactionImpl.BuilderImpl builder = TransactionImpl.newTransactionBuilder(transactionBytes, prunableAttachments);
            this.transaction = builder.build();
            this.transaction.setHeight(rs.getInt("transaction_height"));
            this.arrivalTimestamp = rs.getLong("arrival_timestamp");
            this.feePerByte = rs.getLong("fee_per_byte");
        } catch (EcValidationException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    void saveUnconfirmedTransaction(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO unconfirmed_transaction (id, transaction_height, "
                + "fee_per_byte, expiration, transaction_bytes, prunable_json, arrival_timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, transaction.getTransactionId());
            pstmt.setInt(++i, transaction.getTransactionHeight());
            pstmt.setLong(++i, feePerByte);
            pstmt.setInt(++i, transaction.getExpiration());
            pstmt.setBytes(++i, transaction.bytes());
            JSONObject prunableJSON = transaction.getPrunableAttachmentJSON();
            if (prunableJSON != null) {
                pstmt.setString(++i, prunableJSON.toJSONString());
            } else {
                pstmt.setNull(++i, Types.VARCHAR);
            }
            pstmt.setLong(++i, arrivalTimestamp);
            pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
            pstmt.executeUpdate();
        }
    }

    TransactionImpl getTransaction() {
        return transaction;
    }

    long getArrivalTimestamp() {
        return arrivalTimestamp;
    }

    long getFeePerByte() {
        return feePerByte;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UnconfirmedTransaction && transaction.equals(((UnconfirmedTransaction) o).getTransaction());
    }

    @Override
    public int hashCode() {
        return transaction.hashCode();
    }

    @Override
    public long getTransactionId() {
        return transaction.getTransactionId();
    }

    H2Key getDbKey() {
        return transaction.getH2Key();
    }

    @Override
    public String getStringId() {
        return transaction.getStringId();
    }

    @Override
    public long getSenderId() {
        return transaction.getSenderId();
    }

    @Override
    public byte[] getSenderPublicKey() {
        return transaction.getSenderPublicKey();
    }

    @Override
    public long getRecipientId() {
        return transaction.getRecipientId();
    }

    @Override
    public int getTransactionHeight() {
        return transaction.getTransactionHeight();
    }

    @Override
    public long getBlockId() {
        return transaction.getBlockId();
    }

    @Override
    public EcBlock getBlock() {
        return transaction.getBlock();
    }

    @Override
    public int getTimestamp() {
        return transaction.getTimestamp();
    }

    @Override
    public int getBlockTimestamp() {
        return transaction.getBlockTimestamp();
    }

    @Override
    public short getDeadline() {
        return transaction.getDeadline();
    }

    @Override
    public int getExpiration() {
        return transaction.getExpiration();
    }

    @Override
    public long getAmountNQT() {
        return transaction.getAmountNQT();
    }

    @Override
    public long getFeeNQT() {
        return transaction.getFeeNQT();
    }

    @Override
    public String getReferencedTransactionFullHash() {
        return transaction.getReferencedTransactionFullHash();
    }

    @Override
    public byte[] getSignature() {
        return transaction.getSignature();
    }

    @Override
    public String getFullHash() {
        return transaction.getFullHash();
    }

    @Override
    public TransactionType getTransactionType() {
        return transaction.getTransactionType();
    }

    @Override
    public Mortgaged getAttachment() {
        return transaction.getAttachment();
    }

    @Override
    public boolean verifySignature() {
        return transaction.verifySignature();
    }

    @Override
    public void validate() throws EcValidationException {
        transaction.validate();
    }

    @Override
    public byte[] getBytes() {
        return transaction.getBytes();
    }

    @Override
    public byte[] getUnsignedBytes() {
        return transaction.getUnsignedBytes();
    }

    @Override
    public JSONObject getJSONObject() {
        return transaction.getJSONObject();
    }

    @Override
    public JSONObject getPrunableAttachmentJSON() {
        return transaction.getPrunableAttachmentJSON();
    }

    @Override
    public byte getVersion() {
        return transaction.getVersion();
    }

    @Override
    public int getFullSize() {
        return transaction.getFullSize();
    }

    @Override
    public Message getMessage() {
        return transaction.getMessage();
    }

    @Override
    public PrunablePlainMessage getPrunablePlainMessage() {
        return transaction.getPrunablePlainMessage();
    }

    @Override
    public Enclosure.EncryptedMessage getEncryptedMessage() {
        return transaction.getEncryptedMessage();
    }

    @Override
    public Enclosure.PrunableEncryptedMessage getPrunableEncryptedMessage() {
        return transaction.getPrunableEncryptedMessage();
    }

    public Enclosure.EncryptToSelfMessage getEncryptToSelfMessage() {
        return transaction.getEncryptToSelfMessage();
    }

    @Override
    public Phasing getPhasing() {
        return transaction.getPhasing();
    }

    @Override
    public List<? extends Enclosure> getAppendages() {
        return transaction.getAppendages();
    }

    @Override
    public List<? extends Enclosure> getAppendages(boolean includeExpiredPrunable) {
        return transaction.getAppendages(includeExpiredPrunable);
    }

    @Override
    public List<? extends Enclosure> getAppendages(Filter<Enclosure> filter, boolean includeExpiredPrunable) {
        return transaction.getAppendages(filter, includeExpiredPrunable);
    }

    @Override
    public int getECBlockHeight() {
        return transaction.getECBlockHeight();
    }

    @Override
    public long getECBlockId() {
        return transaction.getECBlockId();
    }

    @Override
    public short getTransactionIndex() {
        return transaction.getTransactionIndex();
    }
}
