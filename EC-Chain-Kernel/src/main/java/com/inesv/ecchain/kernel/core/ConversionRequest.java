package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ConversionRequest {

    private static final H2KeyLongKeyFactory<ConversionRequest> EXCHANGE_REQUEST_DB_KEY_FACTORY = new H2KeyLongKeyFactory<ConversionRequest>("Id") {

        @Override
        public H2Key newKey(ConversionRequest conversionRequest) {
            return conversionRequest.h2Key;
        }

    };

    private static final EntityH2Table<ConversionRequest> EXCHANGE_REQUEST_TABLE = new EntityH2Table<ConversionRequest>("exchange_request", EXCHANGE_REQUEST_DB_KEY_FACTORY) {

        @Override
        protected ConversionRequest load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new ConversionRequest(rs, h2Key);
        }

        @Override
        protected void save(Connection con, ConversionRequest conversionRequest) throws SQLException {
            conversionRequest.save(con);
        }

    };
    private final long id;
    private final long accountId;
    private final long currencyId;
    private final int height;
    private final int timestamp;
    private final H2Key h2Key;
    private final long units;
    private final long rate;
    private final boolean isBuy;


    private ConversionRequest(Transaction transaction, Mortgaged.MonetarySystemExchangeBuy attachment) {
        this(transaction, attachment, true);
    }
    private ConversionRequest(Transaction transaction, Mortgaged.MonetarySystemExchangeSell attachment) {
        this(transaction, attachment, false);
    }
    private ConversionRequest(Transaction transaction, Mortgaged.MonetarySystemExchange attachment, boolean isBuy) {
        this.id = transaction.getTransactionId();
        this.h2Key = EXCHANGE_REQUEST_DB_KEY_FACTORY.newKey(this.id);
        this.accountId = transaction.getSenderId();
        this.currencyId = attachment.getCurrencyId();
        this.units = attachment.getUnits();
        this.rate = attachment.getRateNQT();
        this.isBuy = isBuy;
        EcBlock ecBlock = EcBlockchainImpl.getInstance().getLastECBlock();
        this.height = ecBlock.getHeight();
        this.timestamp = ecBlock.getTimestamp();
    }
    private ConversionRequest(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.accountId = rs.getLong("account_id");
        this.currencyId = rs.getLong("currency_id");
        this.units = rs.getLong("units");
        this.rate = rs.getLong("rate");
        this.isBuy = rs.getBoolean("is_buy");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
    }

    public static int getCount() {
        return EXCHANGE_REQUEST_TABLE.getCount();
    }

    public static H2Iterator<ConversionRequest> getAccountCurrencyExchangeRequests(long accountId, long currencyId, int from, int to) {
        return EXCHANGE_REQUEST_TABLE.getManyBy(new H2ClauseLongClause("account_id", accountId).and(new H2ClauseLongClause("currency_id", currencyId)), from, to);
    }

    static void addExchangeRequest(Transaction transaction, Mortgaged.MonetarySystemExchangeBuy attachment) {
        ConversionRequest conversionRequest = new ConversionRequest(transaction, attachment);
        EXCHANGE_REQUEST_TABLE.insert(conversionRequest);
    }

    static void addExchangeRequest(Transaction transaction, Mortgaged.MonetarySystemExchangeSell attachment) {
        ConversionRequest conversionRequest = new ConversionRequest(transaction, attachment);
        EXCHANGE_REQUEST_TABLE.insert(conversionRequest);
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO exchange_request (id, account_id, currency_id, "
                + "units, rate, is_buy, timestamp, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setLong(++i, this.currencyId);
            pstmt.setLong(++i, this.units);
            pstmt.setLong(++i, this.rate);
            pstmt.setBoolean(++i, this.isBuy);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, this.height);
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
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

    public long getRate() {
        return rate;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

}
