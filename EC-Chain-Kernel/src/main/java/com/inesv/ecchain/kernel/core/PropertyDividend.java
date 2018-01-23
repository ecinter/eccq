package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.util.ListenerManager;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PropertyDividend {

    private static final ListenerManager<PropertyDividend, PropertyDividendEvent> PROPERTY_DIVIDEND_EVENT_LISTENER_MANAGER = new ListenerManager<>();
    private static final H2KeyLongKeyFactory<PropertyDividend> DIVIDEND_DB_KEY_FACTORY = new H2KeyLongKeyFactory<PropertyDividend>("Id") {

        @Override
        public H2Key newKey(PropertyDividend propertyDividend) {
            return propertyDividend.h2Key;
        }

    };
    private static final EntityH2Table<PropertyDividend> ASSET_DIVIDEND_TABLE = new EntityH2Table<PropertyDividend>("asset_dividend", DIVIDEND_DB_KEY_FACTORY) {

        @Override
        protected PropertyDividend load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new PropertyDividend(rs, h2Key);
        }

        @Override
        protected void save(Connection con, PropertyDividend propertyDividend) throws SQLException {
            propertyDividend.save(con);
        }

    };
    private final long id;
    private final H2Key h2Key;
    private final long assetId;
    private final long amountNQTPerQNT;
    private final int dividendHeight;
    private final long totalDividend;
    private final long numAccounts;
    private final int timestamp;
    private final int height;
    private PropertyDividend(long transactionId, Mortgaged.ColoredCoinsDividendPayment attachment,
                             long totalDividend, long numAccounts) {
        this.id = transactionId;
        this.h2Key = DIVIDEND_DB_KEY_FACTORY.newKey(this.id);
        this.assetId = attachment.getAssetId();
        this.amountNQTPerQNT = attachment.getAmountNQTPerQNT();
        this.dividendHeight = attachment.getHeight();
        this.totalDividend = totalDividend;
        this.numAccounts = numAccounts;
        this.timestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
        this.height = EcBlockchainImpl.getInstance().getHeight();
    }
    private PropertyDividend(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.assetId = rs.getLong("asset_id");
        this.amountNQTPerQNT = rs.getLong("amount");
        this.dividendHeight = rs.getInt("dividend_height");
        this.totalDividend = rs.getLong("total_dividend");
        this.numAccounts = rs.getLong("num_accounts");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
    }

    public static H2Iterator<PropertyDividend> getAssetDividends(long assetId, int from, int to) {
        return ASSET_DIVIDEND_TABLE.getManyBy(new H2ClauseLongClause("asset_id", assetId), from, to);
    }

    public static PropertyDividend getLastDividend(long assetId) {
        try (H2Iterator<PropertyDividend> dividends = ASSET_DIVIDEND_TABLE.getManyBy(new H2ClauseLongClause("asset_id", assetId), 0, 0)) {
            if (dividends.hasNext()) {
                return dividends.next();
            }
        }
        return null;
    }

    static PropertyDividend addPropertyDividend(long transactionId, Mortgaged.ColoredCoinsDividendPayment attachment,
                                                long totalDividend, long numAccounts) {
        PropertyDividend propertyDividend = new PropertyDividend(transactionId, attachment, totalDividend, numAccounts);
        ASSET_DIVIDEND_TABLE.insert(propertyDividend);
        PROPERTY_DIVIDEND_EVENT_LISTENER_MANAGER.notify(propertyDividend, PropertyDividendEvent.ASSET_DIVIDEND);
        return propertyDividend;
    }

    public static void start() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_dividend (id, asset_id, "
                + "amount, dividend_height, total_dividend, num_accounts, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.amountNQTPerQNT);
            pstmt.setInt(++i, this.dividendHeight);
            pstmt.setLong(++i, this.totalDividend);
            pstmt.setLong(++i, this.numAccounts);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, this.height);
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getAssetId() {
        return assetId;
    }

    public long getAmountNQTPerQNT() {
        return amountNQTPerQNT;
    }

    public int getDividendHeight() {
        return dividendHeight;
    }

    public long getTotalDividend() {
        return totalDividend;
    }

    public long getNumAccounts() {
        return numAccounts;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

}
