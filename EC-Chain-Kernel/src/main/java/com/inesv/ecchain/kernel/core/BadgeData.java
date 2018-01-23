package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.Search;
import com.inesv.ecchain.kernel.H2.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BadgeData {
    private static final H2KeyLongKeyFactory<BadgeData> TAGGED_DATA_KEY_FACTORY = new H2KeyLongKeyFactory<BadgeData>("Id") {

        @Override
        public H2Key newKey(BadgeData badgeData) {
            return badgeData.h2Key;
        }

    };
    private static final VersionedPrunableH2Table<BadgeData> TAGGED_DATA_TABLE = new VersionedPrunableH2Table<BadgeData>(
            "tagged_data", TAGGED_DATA_KEY_FACTORY, "name,description,tags") {

        @Override
        protected BadgeData load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new BadgeData(rs, h2Key);
        }

        @Override
        protected void save(Connection con, BadgeData badgeData) throws SQLException {
            badgeData.saveBadgeData(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY block_timestamp DESC, height DESC, db_id DESC ";
        }

        @Override
        protected void prune() {
            if (Constants.EC_ENABLE_PRUNING) {
                try (Connection con = h2.getConnection();
                     PreparedStatement pstmtSelect = con.prepareStatement("SELECT parsed_tags "
                             + "FROM tagged_data WHERE transaction_timestamp < ? AND latest = TRUE ")) {
                    int expiration = new EcTime.EpochEcTime().getTime() - Constants.EC_MAX_PRUNABLE_LIFETIME;
                    pstmtSelect.setInt(1, expiration);
                    Map<String, Integer> expiredTags = new HashMap<>();
                    try (ResultSet rs = pstmtSelect.executeQuery()) {
                        while (rs.next()) {
                            Object[] array = (Object[]) rs.getArray("parsed_tags").getArray();
                            for (Object tag : array) {
                                Integer count = expiredTags.get(tag);
                                expiredTags.put((String) tag, count != null ? count + 1 : 1);
                            }
                        }
                    }
                    Tag.delete(expiredTags);
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
            }
            super.prune();
        }

    };
    private static final H2KeyLongKeyFactory<Timestamp> TIMESTAMP_KEY_FACTORY = new H2KeyLongKeyFactory<Timestamp>("Id") {

        @Override
        public H2Key newKey(Timestamp timestamp) {
            return timestamp.h2Key;
        }

    };
    private static final VersionedEntityH2Table<Timestamp> TIMESTAMP_TABLE = new VersionedEntityH2Table<Timestamp>(
            "tagged_data_timestamp", TIMESTAMP_KEY_FACTORY) {

        @Override
        protected Timestamp load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
            return new Timestamp(rs, h2Key);
        }

        @Override
        protected void save(Connection con, Timestamp timestamp) throws SQLException {
            timestamp.save(con);
        }

    };
    private static final H2KeyLongKeyFactory<Long> EXTEND_DB_KEY_FACTORY = new H2KeyLongKeyFactory<Long>("Id") {

        @Override
        public H2Key newKey(Long taggedDataId) {
            return newKey(taggedDataId.longValue());
        }

    };
    private static final VersionedValuesH2Table<Long, Long> EXTEND_TABLE = new VersionedValuesH2Table<Long, Long>("tagged_data_extend", EXTEND_DB_KEY_FACTORY) {

        @Override
        protected Long load(Connection con, ResultSet rs) throws SQLException {
            return rs.getLong("extend_id");
        }

        @Override
        protected void save(Connection con, Long taggedDataId, Long extendId) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO tagged_data_extend (id, extend_id, "
                    + "height, latest) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, taggedDataId);
                pstmt.setLong(++i, extendId);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

    };
    private final long id;
    private final H2Key h2Key;
    private final long accountId;
    private final String name;
    private final String description;
    private final String tags;
    private final String[] parsedTags;
    private final byte[] data;
    private final String type;
    private final String channel;
    private final boolean isText;
    private final String filename;
    private int transactionTimestamp;
    private int blockTimestamp;
    private int height;

    public BadgeData(Transaction transaction, Mortgaged.TaggedDataMortgaged attachment) {
        this(transaction, attachment, EcBlockchainImpl.getInstance().getLastBlockTimestamp(), EcBlockchainImpl.getInstance().getHeight());
    }

    private BadgeData(Transaction transaction, Mortgaged.TaggedDataMortgaged attachment, int blockTimestamp, int height) {
        this.id = transaction.getTransactionId();
        this.h2Key = TAGGED_DATA_KEY_FACTORY.newKey(this.id);
        this.accountId = transaction.getSenderId();
        this.name = attachment.getName();
        this.description = attachment.getDescription();
        this.tags = attachment.getTags();
        this.parsedTags = Search.parseTags(tags, 3, 20, 5);
        this.data = attachment.getData();
        this.type = attachment.getType();
        this.channel = attachment.getChannel();
        this.isText = attachment.isText();
        this.filename = attachment.getFilename();
        this.blockTimestamp = blockTimestamp;
        this.transactionTimestamp = transaction.getTimestamp();
        this.height = height;
    }

    private BadgeData(ResultSet rs, H2Key h2Key) throws SQLException {
        this.id = rs.getLong("Id");
        this.h2Key = h2Key;
        this.accountId = rs.getLong("account_id");
        this.name = rs.getString("name");
        this.description = rs.getString("description");
        this.tags = rs.getString("tags");
        this.parsedTags = H2Utils.h2getArray(rs, "parsed_tags", String[].class);
        this.data = rs.getBytes("data");
        this.type = rs.getString("type");
        this.channel = rs.getString("channel");
        this.isText = rs.getBoolean("is_text");
        this.filename = rs.getString("filename");
        this.blockTimestamp = rs.getInt("block_timestamp");
        this.transactionTimestamp = rs.getInt("transaction_timestamp");
        this.height = rs.getInt("height");
    }

    public static int getCount() {
        return TAGGED_DATA_TABLE.getCount();
    }

    public static H2Iterator<BadgeData> getAll(int from, int to) {
        return TAGGED_DATA_TABLE.getAll(from, to);
    }

    public static BadgeData getData(long transactionId) {
        return TAGGED_DATA_TABLE.get(TAGGED_DATA_KEY_FACTORY.newKey(transactionId));
    }

    public static List<Long> getExtendTransactionIds(long taggedDataId) {
        return EXTEND_TABLE.get(EXTEND_DB_KEY_FACTORY.newKey(taggedDataId));
    }

    public static H2Iterator<BadgeData> getData(String channel, long accountId, int from, int to) {
        if (channel == null && accountId == 0) {
            throw new IllegalArgumentException("Either channel, or accountId, or both, must be specified");
        }
        return TAGGED_DATA_TABLE.getManyBy(getDbClause(channel, accountId), from, to);
    }

    public static H2Iterator<BadgeData> searchData(String query, String channel, long accountId, int from, int to) {
        return TAGGED_DATA_TABLE.search(query, getDbClause(channel, accountId), from, to,
                " ORDER BY ft.score DESC, tagged_data.block_timestamp DESC, tagged_data.db_id DESC ");
    }

    private static H2Clause getDbClause(String channel, long accountId) {
        H2Clause h2Clause = H2Clause.EMPTY_CLAUSE;
        if (channel != null) {
            h2Clause = new H2ClauseStringClause("channel", channel);
        }
        if (accountId != 0) {
            H2Clause accountClause = new H2ClauseLongClause("account_id", accountId);
            h2Clause = h2Clause != H2Clause.EMPTY_CLAUSE ? h2Clause.and(accountClause) : accountClause;
        }
        return h2Clause;
    }

    public static void start() {
        Tag.init();
    }

    static void addBadgeData(TransactionImpl transaction, Mortgaged.TaggedDataUpload attachment) {
        if (new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() < Constants.EC_MAX_PRUNABLE_LIFETIME && attachment.getData() != null) {
            BadgeData badgeData = TAGGED_DATA_TABLE.get(transaction.getH2Key());
            if (badgeData == null) {
                badgeData = new BadgeData(transaction, attachment);
                TAGGED_DATA_TABLE.insert(badgeData);
                Tag.add(badgeData);
            }
        }
        Timestamp timestamp = new Timestamp(transaction.getTransactionId(), transaction.getTimestamp());
        TIMESTAMP_TABLE.insert(timestamp);
    }

    static void extend(Transaction transaction, Mortgaged.TaggedDataExtend attachment) {
        long taggedDataId = attachment.getTaggedDataId();
        H2Key h2Key = TAGGED_DATA_KEY_FACTORY.newKey(taggedDataId);
        Timestamp timestamp = TIMESTAMP_TABLE.get(h2Key);
        if (transaction.getTimestamp() - Constants.EC_MIN_PRUNABLE_LIFETIME > timestamp.timestamp) {
            timestamp.timestamp = transaction.getTimestamp();
        } else {
            timestamp.timestamp = timestamp.timestamp + Math.min(Constants.EC_MIN_PRUNABLE_LIFETIME, Integer.MAX_VALUE - timestamp.timestamp);
        }
        TIMESTAMP_TABLE.insert(timestamp);
        List<Long> extendTransactionIds = EXTEND_TABLE.get(h2Key);
        extendTransactionIds.add(transaction.getTransactionId());
        EXTEND_TABLE.insert(taggedDataId, extendTransactionIds);
        if (new EcTime.EpochEcTime().getTime() - Constants.EC_MAX_PRUNABLE_LIFETIME < timestamp.timestamp) {
            BadgeData badgeData = TAGGED_DATA_TABLE.get(h2Key);
            if (badgeData == null && attachment.getData() != null) {
                TransactionImpl uploadTransaction = TransactionH2.selectTransaction(taggedDataId);
                badgeData = new BadgeData(uploadTransaction, attachment);
                Tag.add(badgeData);
            }
            if (badgeData != null) {
                badgeData.transactionTimestamp = timestamp.timestamp;
                badgeData.blockTimestamp = EcBlockchainImpl.getInstance().getLastBlockTimestamp();
                badgeData.height = EcBlockchainImpl.getInstance().getHeight();
                TAGGED_DATA_TABLE.insert(badgeData);
            }
        }
    }

    static void restore(Transaction transaction, Mortgaged.TaggedDataUpload attachment, int blockTimestamp, int height) {
        BadgeData badgeData = new BadgeData(transaction, attachment, blockTimestamp, height);
        TAGGED_DATA_TABLE.insert(badgeData);
        Tag.add(badgeData, height);
        int timestamp = transaction.getTimestamp();
        for (long extendTransactionId : BadgeData.getExtendTransactionIds(transaction.getTransactionId())) {
            Transaction extendTransaction = TransactionH2.selectTransaction(extendTransactionId);
            if (extendTransaction.getTimestamp() - Constants.EC_MIN_PRUNABLE_LIFETIME > timestamp) {
                timestamp = extendTransaction.getTimestamp();
            } else {
                timestamp = timestamp + Math.min(Constants.EC_MIN_PRUNABLE_LIFETIME, Integer.MAX_VALUE - timestamp);
            }
            badgeData.transactionTimestamp = timestamp;
            badgeData.blockTimestamp = extendTransaction.getBlockTimestamp();
            badgeData.height = extendTransaction.getTransactionHeight();
            TAGGED_DATA_TABLE.insert(badgeData);
        }
    }

    static boolean isPruned(long transactionId) {
        try (Connection con = H2.H2.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT 1 FROM tagged_data WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return !rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void saveBadgeData(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO tagged_data (id, account_id, name, description, tags, parsed_tags, "
                + "type, channel, data, is_text, filename, block_timestamp, transaction_timestamp, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setString(++i, this.name);
            pstmt.setString(++i, this.description);
            pstmt.setString(++i, this.tags);
            H2Utils.h2setArray(pstmt, ++i, this.parsedTags);
            pstmt.setString(++i, this.type);
            pstmt.setString(++i, this.channel);
            pstmt.setBytes(++i, this.data);
            pstmt.setBoolean(++i, this.isText);
            pstmt.setString(++i, this.filename);
            pstmt.setInt(++i, this.blockTimestamp);
            pstmt.setInt(++i, this.transactionTimestamp);
            pstmt.setInt(++i, height);
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTags() {
        return tags;
    }

    public String[] getParsedTags() {
        return parsedTags;
    }

    public byte[] getData() {
        return data;
    }

    public String getType() {
        return type;
    }

    public String getChannel() {
        return channel;
    }

    public boolean isText() {
        return isText;
    }

    public String getFilename() {
        return filename;
    }

    public int getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public int getBlockTimestamp() {
        return blockTimestamp;
    }

    private static final class Timestamp {

        private final long id;
        private final H2Key h2Key;
        private int timestamp;

        private Timestamp(long id, int timestamp) {
            this.id = id;
            this.h2Key = TIMESTAMP_KEY_FACTORY.newKey(this.id);
            this.timestamp = timestamp;
        }

        private Timestamp(ResultSet rs, H2Key h2Key) throws SQLException {
            this.id = rs.getLong("Id");
            this.h2Key = h2Key;
            this.timestamp = rs.getInt("timestamp");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO tagged_data_timestamp (id, timestamp, height, latest) "
                    + "KEY (id, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setInt(++i, this.timestamp);
                pstmt.setInt(++i, EcBlockchainImpl.getInstance().getHeight());
                pstmt.executeUpdate();
            }
        }

    }

    public static final class Tag {

        private static final H2KeyStringKeyFactory<Tag> tagDbKeyFactory = new H2KeyStringKeyFactory<Tag>("tag") {
            @Override
            public H2Key newKey(Tag tag) {
                return tag.h2Key;
            }
        };

        private static final VersionedPersistentH2Table<Tag> tagTable = new VersionedPersistentH2Table<Tag>("data_tag", tagDbKeyFactory) {

            @Override
            protected Tag load(Connection con, ResultSet rs, H2Key h2Key) throws SQLException {
                return new Tag(rs, h2Key);
            }

            @Override
            protected void save(Connection con, Tag tag) throws SQLException {
                tag.save(con);
            }

            @Override
            public String defaultSort() {
                return " ORDER BY tag_count DESC, tag ASC ";
            }

        };
        private final String tag;
        private final H2Key h2Key;
        private final int height;
        private int count;

        private Tag(String tag, int height) {
            this.tag = tag;
            this.h2Key = tagDbKeyFactory.newKey(this.tag);
            this.height = height;
        }

        private Tag(ResultSet rs, H2Key h2Key) throws SQLException {
            this.tag = rs.getString("tag");
            this.h2Key = h2Key;
            this.count = rs.getInt("tag_count");
            this.height = rs.getInt("height");
        }

        public static int getTagCount() {
            return tagTable.getCount();
        }

        public static H2Iterator<Tag> getAllTags(int from, int to) {
            return tagTable.getAll(from, to);
        }

        public static H2Iterator<Tag> getTagsLike(String prefix, int from, int to) {
            H2Clause h2Clause = new H2ClauseLikeClause("tag", prefix);
            return tagTable.getManyBy(h2Clause, from, to, " ORDER BY tag ");
        }

        private static void init() {
        }

        private static void add(BadgeData badgeData) {
            for (String tagValue : badgeData.getParsedTags()) {
                Tag tag = tagTable.get(tagDbKeyFactory.newKey(tagValue));
                if (tag == null) {
                    tag = new Tag(tagValue, EcBlockchainImpl.getInstance().getHeight());
                }
                tag.count += 1;
                tagTable.insert(tag);
            }
        }

        private static void add(BadgeData badgeData, int height) {
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("UPDATE data_tag SET tag_count = tag_count + 1 WHERE tag = ? AND height >= ?")) {
                for (String tagValue : badgeData.getParsedTags()) {
                    pstmt.setString(1, tagValue);
                    pstmt.setInt(2, height);
                    int updated = pstmt.executeUpdate();
                    if (updated == 0) {
                        Tag tag = new Tag(tagValue, height);
                        tag.count += 1;
                        tagTable.insert(tag);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        private static void delete(Map<String, Integer> expiredTags) {
            try (Connection con = H2.H2.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("UPDATE data_tag SET tag_count = tag_count - ? WHERE tag = ?");
                 PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM data_tag WHERE tag_count <= 0")) {
                for (Map.Entry<String, Integer> entry : expiredTags.entrySet()) {
                    pstmt.setInt(1, entry.getValue());
                    pstmt.setString(2, entry.getKey());
                    pstmt.executeUpdate();
                    LoggerUtil.logDebug("Reduced tag count for " + entry.getKey() + " by " + entry.getValue());
                }
                int deleted = pstmtDelete.executeUpdate();
                if (deleted > 0) {
                    LoggerUtil.logDebug("Deleted " + deleted + " tags");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO data_tag (tag, tag_count, height, latest) "
                    + "KEY (tag, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setString(++i, this.tag);
                pstmt.setInt(++i, this.count);
                pstmt.setInt(++i, this.height);
                pstmt.executeUpdate();
            }
        }

        public String getTag() {
            return tag;
        }

        public int getCount() {
            return count;
        }

    }

}
