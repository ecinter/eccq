package com.inesv.ecchain.kernel.H2;


import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.ReadWriteManager;
import com.inesv.ecchain.kernel.core.H2;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.h2.api.Trigger;
import org.h2.tools.SimpleResultSet;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class FullTextTrigger implements Trigger, TransactionCallback {


    private static final ConcurrentHashMap<String, FullTextTrigger> STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();

    private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();

    private static final ReadWriteManager READ_WRITE_MANAGER = new ReadWriteManager();

    private static final Analyzer STANDARD_ANALYZER = new StandardAnalyzer();
 
    private static volatile boolean isActive = false;
 
    private static Path indexPath;
  
    private static Directory directory;

    private static DirectoryReader indexReader;

    private static IndexSearcher indexSearcher;

    private static IndexWriter indexWriter;

    private final List<String> columnNames = new ArrayList<>();

    private final List<String> columnTypes = new ArrayList<>();

    private final List<Integer> indexColumns = new ArrayList<>();

    private final List<TableUpdate> tableUpdates = new ArrayList<>();

    private volatile boolean isEnabled = false;

    private String tableName;

    private int dbColumn = -1;

    public static void setActive(boolean active) {
        isActive = active;
        if (!active) {
            STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.values().forEach((trigger) -> trigger.isEnabled = false);
            STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.clear();
            removeIndexAccess();
        }
    }

    public static void init() {
        String ourClassName = FullTextTrigger.class.getName();
        try (Connection conn = H2.H2.getConnection();
             Statement stmt = conn.createStatement();
             Statement qstmt = conn.createStatement()) {
            //
            // Check if we have already been initialized.
            //
            boolean alreadyInitialized = true;
            boolean triggersExist = false;
            try (ResultSet rs = qstmt.executeQuery("SELECT JAVA_CLASS FROM INFORMATION_SCHEMA.TRIGGERS "
                    + "WHERE SUBSTRING(TRIGGER_NAME, 0, 4) = 'FTL_'")) {
                while (rs.next()) {
                    triggersExist = true;
                    if (!rs.getString(1).startsWith(ourClassName)) {
                        alreadyInitialized = false;
                    }
                }
            }
            if (triggersExist && alreadyInitialized) {
                LoggerUtil.logInfo("NRS fulltext support is already initialized");
                return;
            }
            //
            // We need to delete an existing Lucene index since the V3 file format is not compatible with V5
            //
            getIndexPath(conn);
            removeIndexFiles(conn);
            //
            // Drop the H2 Lucene V3 function aliases
            //
            stmt.execute("DROP ALIAS IF EXISTS FTL_INIT");
            stmt.execute("DROP ALIAS IF EXISTS FTL_CREATE_INDEX");
            stmt.execute("DROP ALIAS IF EXISTS FTL_DROP_INDEX");
            stmt.execute("DROP ALIAS IF EXISTS FTL_DROP_ALL");
            stmt.execute("DROP ALIAS IF EXISTS FTL_REINDEX");
            stmt.execute("DROP ALIAS IF EXISTS FTL_SEARCH");
            stmt.execute("DROP ALIAS IF EXISTS FTL_SEARCH_DATA");
            LoggerUtil.logInfo("H2 fulltext function aliases dropped");
            //
            // Create our schema and table
            //
            stmt.execute("CREATE SCHEMA IF NOT EXISTS FTL");
            stmt.execute("CREATE TABLE IF NOT EXISTS FTL.INDEXES "
                    + "(SCHEMA VARCHAR, TABLE VARCHAR, COLUMNS VARCHAR, PRIMARY KEY(SCHEMA, TABLE))");
            LoggerUtil.logInfo("NRS fulltext schema created");
            //
            // Drop existing triggers and create our triggers.  H2 will initialize the trigger
            // when it is created.  H2 has already initialized the existing triggers and they
            // will be closed when dropped.  The H2 Lucene V3 trigger initialization will work with
            // Lucene V5, so we are able to open the database using the Lucene V5 library files.
            //
            try (ResultSet rs = qstmt.executeQuery("SELECT * FROM FTL.INDEXES")) {
                while (rs.next()) {
                    String schema = rs.getString("SCHEMA");
                    String table = rs.getString("TABLE");
                    stmt.execute("DROP TRIGGER IF EXISTS FTL_" + table);
                    stmt.execute(String.format("CREATE TRIGGER FTL_%s AFTER INSERT,UPDATE,DELETE ON %s.%s "
                                    + "FOR EACH ROW CALL \"%s\"",
                            table, schema, table, ourClassName));
                }
            }
            //
            // Rebuild the Lucene index since the Lucene V3 index is not compatible with Lucene V5
            //
            reindex(conn);
            //
            // Create our function aliases
            //
            stmt.execute("CREATE ALIAS FTL_CREATE_INDEX FOR \"" + ourClassName + ".createIndex\"");
            stmt.execute("CREATE ALIAS FTL_DROP_INDEX FOR \"" + ourClassName + ".dropIndex\"");
            stmt.execute("CREATE ALIAS FTL_SEARCH NOBUFFER FOR \"" + ourClassName + ".search\"");
            LoggerUtil.logInfo("NRS fulltext aliases created");
        } catch (SQLException exc) {
            LoggerUtil.logError("Unable to initialize NRS fulltext search support", exc);
            throw new RuntimeException(exc.toString(), exc);
        }
    }
    
    public static void reindex(Connection conn) throws SQLException {
        LoggerUtil.logInfo("Rebuilding the Lucene search index");
        try {
            //
            // Delete the current Lucene index
            //
            removeIndexFiles(conn);
            //
            // Reindex each table
            //
            for (FullTextTrigger trigger : STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.values()) {
                trigger.reindexTable(conn);
            }
        } catch (SQLException exc) {
            throw new SQLException("Unable to rebuild the Lucene index", exc);
        }
        LoggerUtil.logInfo("Lucene search index successfully rebuilt");
    }
    
    public static void createIndex(Connection conn, String schema, String table, String columnList)
            throws SQLException {
        String upperSchema = schema.toUpperCase();
        String upperTable = table.toUpperCase();
        String tableName = upperSchema + "." + upperTable;
        getIndexAccess(conn);
        //
        // Drop an existing index and the associated database trigger
        //
        dropIndex(conn, schema, table);
        //
        // Update our schema and create a new database trigger.  Note that the trigger
        // will be initialized when it is created.
        //
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("INSERT INTO FTL.INDEXES (schema, table, columns) "
                            + "VALUES('%s', '%s', '%s')",
                    upperSchema, upperTable, columnList.toUpperCase()));
            stmt.execute(String.format("CREATE TRIGGER FTL_%s AFTER INSERT,UPDATE,DELETE ON %s "
                            + "FOR EACH ROW CALL \"%s\"",
                    upperTable, tableName, FullTextTrigger.class.getName()));
        }
        //
        // Index the table
        //
        FullTextTrigger trigger = STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.get(tableName);
        if (trigger == null) {
            LoggerUtil.logError("NRS fulltext trigger for table " + tableName + " was not initialized");
        } else {
            try {
                trigger.reindexTable(conn);
                LoggerUtil.logInfo("Lucene search index created for table " + tableName);
            } catch (SQLException exc) {
                LoggerUtil.logError("Unable to create Lucene search index for table " + tableName);
                throw new SQLException("Unable to create Lucene search index for table " + tableName, exc);
            }
        }
    }
    
    public static void dropIndex(Connection conn, String schema, String table) throws SQLException {
        String upperSchema = schema.toUpperCase();
        String upperTable = table.toUpperCase();
        boolean reindex = false;
        //
        // Drop an existing database trigger
        //
        try (Statement qstmt = conn.createStatement();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = qstmt.executeQuery(String.format(
                    "SELECT COLUMNS FROM FTL.INDEXES WHERE SCHEMA = '%s' AND TABLE = '%s'",
                    upperSchema, upperTable))) {
                if (rs.next()) {
                    stmt.execute("DROP TRIGGER IF EXISTS FTL_" + upperTable);
                    stmt.execute(String.format("DELETE FROM FTL.INDEXES WHERE SCHEMA = '%s' AND TABLE = '%s'",
                            upperSchema, upperTable));
                    reindex = true;
                }
            }
        }
        //
        // Rebuild the Lucene index
        //
        if (reindex) {
            reindex(conn);
        }
    }
    
    public static void delAll(Connection conn) throws SQLException {
        //
        // Drop existing triggers
        //
        try (Statement qstmt = conn.createStatement();
             Statement stmt = conn.createStatement();
             ResultSet rs = qstmt.executeQuery("SELECT TABLE FROM FTL.INDEXES")) {
            while (rs.next()) {
                String table = rs.getString(1);
                stmt.execute("DROP TRIGGER IF EXISTS FTL_" + table);
            }
            stmt.execute("TRUNCATE TABLE FTL.INDEXES");
            STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.clear();
        }
        //
        // Delete the Lucene index
        //
        removeIndexFiles(conn);
    }
    
    public static ResultSet search(Connection conn, String schema, String table, String queryText, int limit, int offset)
            throws SQLException {
        //
        // Get Lucene index access
        //
        getIndexAccess(conn);
        //
        // Create the result set columns
        //
        SimpleResultSet result = new SimpleResultSet();
        result.addColumn("SCHEMA", Types.VARCHAR, 0, 0);
        result.addColumn("TABLE", Types.VARCHAR, 0, 0);
        result.addColumn("COLUMNS", Types.ARRAY, 0, 0);
        result.addColumn("KEYS", Types.ARRAY, 0, 0);
        result.addColumn("SCORE", Types.FLOAT, 0, 0);
        //
        // Perform the search
        //
        // The _QUERY field contains the table and row identification (schema.table;keyName;keyValue)
        // The _TABLE field is used to limit the search results to the current table
        // The _DATA field contains the indexed row data (this is the default search field)
        // The _MODIFIED field contains the row modification time (YYYYMMDDhhmmss) in GMT
        //
        READ_WRITE_MANAGER.readLock().lock();
        try {
            QueryParser parser = new QueryParser("_DATA", STANDARD_ANALYZER);
            parser.setDateResolution("_MODIFIED", DateTools.Resolution.SECOND);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            Query query = parser.parse("_TABLE:" + schema.toUpperCase() + "." + table.toUpperCase() + " AND (" + queryText + ")");
            TopDocs documents = indexSearcher.search(query, limit);
            ScoreDoc[] hits = documents.scoreDocs;
            int resultCount = Math.min(hits.length, (limit == 0 ? hits.length : limit));
            int resultOffset = Math.min(offset, resultCount);
            for (int i = resultOffset; i < resultCount; i++) {
                Document document = indexSearcher.doc(hits[i].doc);
                String[] indexParts = document.get("_QUERY").split(";");
                String[] nameParts = indexParts[0].split("\\.");
                result.addRow(nameParts[0],
                        nameParts[1],
                        new String[]{indexParts[1]},
                        new Long[]{Long.parseLong(indexParts[2])},
                        hits[i].score);
            }
        } catch (ParseException exc) {
            LoggerUtil.logDebug("Lucene parse exception for query: " + queryText + "\n" + exc.getMessage());
            throw new SQLException("Lucene parse exception for query: " + queryText + "\n" + exc.getMessage());
        } catch (IOException exc) {
            LoggerUtil.logError("Unable to search Lucene index", exc);
            throw new SQLException("Unable to search Lucene index", exc);
        } finally {
            READ_WRITE_MANAGER.readLock().unlock();
        }
        return result;
    }
    
    private static void commitIndex() throws SQLException {
        READ_WRITE_MANAGER.writeLock().lock();
        try {
            indexWriter.commit();
            DirectoryReader newReader = DirectoryReader.openIfChanged(indexReader);
            if (newReader != null) {
                indexReader.close();
                indexReader = newReader;
                indexSearcher = new IndexSearcher(indexReader);
            }
        } catch (IOException exc) {
            LoggerUtil.logError("Unable to commit Lucene index updates", exc);
            throw new SQLException("Unable to commit Lucene index updates", exc);
        } finally {
            READ_WRITE_MANAGER.writeLock().unlock();
        }
    }
    
    private static void getIndexPath(Connection conn) throws SQLException {
        READ_WRITE_MANAGER.writeLock().lock();
        try {
            if (indexPath == null) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("CALL DATABASE_PATH()")) {
                    rs.next();
                    indexPath = FILE_SYSTEM.getPath(rs.getString(1));
                    if (!Files.exists(indexPath)) {
                        Files.createDirectory(indexPath);
                    }
                } catch (IOException exc) {
                    LoggerUtil.logError("Unable to create the Lucene index directory", exc);
                    throw new SQLException("Unable to create the Lucene index directory", exc);
                }
            }
        } finally {
            READ_WRITE_MANAGER.writeLock().unlock();
        }
    }
    
    private static void getIndexAccess(Connection conn) throws SQLException {
        if (!isActive) {
            throw new SQLException("NRS is no longer active");
        }
        boolean obtainedUpdateLock = false;
        if (!READ_WRITE_MANAGER.writeLock().hasLock()) {
            READ_WRITE_MANAGER.updateLock().lock();
            obtainedUpdateLock = true;
        }
        try {
            if (indexPath == null || indexWriter == null) {
                READ_WRITE_MANAGER.writeLock().lock();
                try {
                    if (indexPath == null) {
                        getIndexPath(conn);
                    }
                    if (directory == null) {
                        directory = FSDirectory.open(indexPath);
                    }
                    if (indexWriter == null) {
                        IndexWriterConfig config = new IndexWriterConfig(STANDARD_ANALYZER);
                        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                        indexWriter = new IndexWriter(directory, config);
                        Document document = new Document();
                        document.add(new StringField("_QUERY", "_CONTROL_DOCUMENT_", Field.Store.YES));
                        indexWriter.updateDocument(new Term("_QUERY", "_CONTROL_DOCUMENT_"), document);
                        indexWriter.commit();
                        indexReader = DirectoryReader.open(directory);
                        indexSearcher = new IndexSearcher(indexReader);
                    }
                } finally {
                    READ_WRITE_MANAGER.writeLock().unlock();
                }
            }
        } catch (IOException | SQLException exc) {
            LoggerUtil.logError("Unable to access the Lucene index", exc);
            throw new SQLException("Unable to access the Lucene index", exc);
        } finally {
            if (obtainedUpdateLock) {
                READ_WRITE_MANAGER.updateLock().unlock();
            }
        }
    }
    
    private static void removeIndexAccess() {
        READ_WRITE_MANAGER.writeLock().lock();
        try {
            if (indexSearcher != null) {
                indexSearcher = null;
            }
            if (indexReader != null) {
                indexReader.close();
                indexReader = null;
            }
            if (indexWriter != null) {
                indexWriter.close();
                indexWriter = null;
            }
        } catch (IOException exc) {
            LoggerUtil.logError("Unable to remove Lucene index access", exc);
        } finally {
            READ_WRITE_MANAGER.writeLock().unlock();
        }
    }
    
    private static void removeIndexFiles(Connection conn) throws SQLException {
        READ_WRITE_MANAGER.writeLock().lock();
        try {
            //
            // Remove current Lucene index access
            //
            removeIndexAccess();
            //
            // Delete the index files
            //
            getIndexPath(conn);
            try (Stream<Path> stream = Files.list(indexPath)) {
                Path[] paths = stream.toArray(Path[]::new);
                for (Path path : paths) {
                    Files.delete(path);
                }
            }
            LoggerUtil.logInfo("Lucene search index deleted");
            //
            // Get Lucene index access once more
            //
            getIndexAccess(conn);
        } catch (IOException exc) {
            LoggerUtil.logError("Unable to remove Lucene index files", exc);
            throw new SQLException("Unable to remove Lucene index files", exc);
        } finally {
            READ_WRITE_MANAGER.writeLock().unlock();
        }
    }

    private void commitRow(Object[] oldRow, Object[] newRow) throws SQLException {
        if (oldRow != null) {
            if (newRow != null) {
                indexRow(newRow);
            } else {
                deleteRow(oldRow);
            }
        } else if (newRow != null) {
            indexRow(newRow);
        }
    }

    private void reindexTable(Connection conn) throws SQLException {
        if (indexColumns.isEmpty()) {
            return;
        }
        //
        // Build the SELECT statement for just the indexed columns
        //
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT DB_ID");
        for (int index : indexColumns) {
            sb.append(", ").append(columnNames.get(index));
        }
        sb.append(" FROM ").append(tableName);
        Object[] row = new Object[columnNames.size()];
        //
        // Index each row in the table
        //
        try (Statement qstmt = conn.createStatement();
             ResultSet rs = qstmt.executeQuery(sb.toString())) {
            while (rs.next()) {
                row[dbColumn] = rs.getObject(1);
                int i = 2;
                for (int index : indexColumns) {
                    row[index] = rs.getObject(i++);
                }
                indexRow(row);
            }
        }
        //
        // Commit the index updates
        //
        commitIndex();
    }

    private void indexRow(Object[] row) throws SQLException {
        READ_WRITE_MANAGER.readLock().lock();
        try {
            String query = tableName + ";" + columnNames.get(dbColumn) + ";" + (Long) row[dbColumn];
            Document document = new Document();
            document.add(new StringField("_QUERY", query, Field.Store.YES));
            long now = System.currentTimeMillis();
            document.add(new TextField("_MODIFIED", DateTools.timeToString(now, DateTools.Resolution.SECOND), Field.Store.NO));
            document.add(new TextField("_TABLE", tableName, Field.Store.NO));
            StringJoiner sj = new StringJoiner(" ");
            for (int index : indexColumns) {
                String data = (row[index] != null ? (String) row[index] : "NULL");
                document.add(new TextField(columnNames.get(index), data, Field.Store.NO));
                sj.add(data);
            }
            document.add(new TextField("_DATA", sj.toString(), Field.Store.NO));
            indexWriter.updateDocument(new Term("_QUERY", query), document);
        } catch (IOException exc) {
            LoggerUtil.logError("Unable to index row", exc);
            throw new SQLException("Unable to index row", exc);
        } finally {
            READ_WRITE_MANAGER.readLock().unlock();
        }
    }

    private void deleteRow(Object[] row) throws SQLException {
        String query = tableName + ";" + columnNames.get(dbColumn) + ";" + (Long) row[dbColumn];
        READ_WRITE_MANAGER.readLock().lock();
        try {
            indexWriter.deleteDocuments(new Term("_QUERY", query));
        } catch (IOException exc) {
            LoggerUtil.logError("Unable to delete indexed row", exc);
            throw new SQLException("Unable to delete indexed row", exc);
        } finally {
            READ_WRITE_MANAGER.readLock().unlock();
        }
    }

    private static class TableUpdate {

        /**
         * Transaction thread
         */
        private final Thread thread;

        /**
         * Old table row
         */
        private final Object[] oldRow;

        /**
         * New table row
         */
        private final Object[] newRow;

        /**
         * Create the table update
         *
         * @param thread Transaction thread
         * @param oldRow Old table row or null
         * @param newRow New table row or null
         */
        public TableUpdate(Thread thread, Object[] oldRow, Object[] newRow) {
            this.thread = thread;
            this.oldRow = oldRow;
            this.newRow = newRow;
        }

        /**
         * Return the transaction thread
         *
         * @return Transaction thread
         */
        public Thread getThread() {
            return thread;
        }

        /**
         * Return the old table row
         *
         * @return Old table row or null
         */
        public Object[] getOldRow() {
            return oldRow;
        }

        /**
         * Return the new table row
         *
         * @return New table row or null
         */
        public Object[] getNewRow() {
            return newRow;
        }
    }
    
    @Override
    public void init(Connection conn, String schema, String trigger, String table, boolean before, int type)
            throws SQLException {
        //
        // Ignore the trigger if NRS is not active or this is a temporary table copy
        //
        if (!isActive || table.contains("_COPY_")) {
            return;
        }
        //
        // Access the Lucene index
        //
        // We need to get the access just once, either in a trigger or in a function alias
        //
        getIndexAccess(conn);
        //
        // Get table and index information
        //
        tableName = schema + "." + table;
        try (Statement stmt = conn.createStatement()) {
            //
            // Get the table column information
            //
            // NRS tables use DB_ID as the primary index
            //
            try (ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + table + " FROM " + schema)) {
                int index = 0;
                while (rs.next()) {
                    String columnName = rs.getString("FIELD");
                    String columnType = rs.getString("TYPE");
                    columnType = columnType.substring(0, columnType.indexOf('('));
                    columnNames.add(columnName);
                    columnTypes.add(columnType);
                    if (columnName.equals("DB_ID")) {
                        dbColumn = index;
                    }
                    index++;
                }
            }
            if (dbColumn < 0) {
                LoggerUtil.logError("DB_ID column not found for table " + tableName);
                return;
            }
            //
            // Get the indexed columns
            //
            // Indexed columns must be strings (VARCHAR)
            //
            try (ResultSet rs = stmt.executeQuery(String.format(
                    "SELECT COLUMNS FROM FTL.INDEXES WHERE SCHEMA = '%s' AND TABLE = '%s'",
                    schema, table))) {
                if (rs.next()) {
                    String[] columns = rs.getString(1).split(",");
                    for (String column : columns) {
                        int pos = columnNames.indexOf(column);
                        if (pos >= 0) {
                            if (columnTypes.get(pos).equals("VARCHAR")) {
                                indexColumns.add(pos);
                            } else {
                                LoggerUtil.logError("Indexed column " + column + " in table " + tableName + " is not a string");
                            }
                        } else {
                            LoggerUtil.logError("Indexed column " + column + " not found in table " + tableName);
                        }
                    }
                }
            }
            if (indexColumns.isEmpty()) {
                LoggerUtil.logError("No indexed columns found for table " + tableName);
                return;
            }
            //
            // Trigger is enabled
            //
            isEnabled = true;
            STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.put(tableName, this);
        } catch (SQLException exc) {
            LoggerUtil.logError("Unable to get table information", exc);
        }
    }
    
    @Override
    public void close() throws SQLException {
        if (isEnabled) {
            isEnabled = false;
            STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.remove(tableName);
        }
    }
    
    @Override
    public void remove() throws SQLException {
        if (isEnabled) {
            isEnabled = false;
            STRING_FULL_TEXT_TRIGGER_CONCURRENT_HASH_MAP.remove(tableName);
        }
    }
    
    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        //
        // Ignore the trigger if it is not enabled
        //
        if (!isEnabled) {
            return;
        }
        //
        // Commit the change immediately if we are not in a transaction
        //
        if (!H2.H2.isInTransaction()) {
            try {
                commitRow(oldRow, newRow);
                commitIndex();
            } catch (SQLException exc) {
                LoggerUtil.logError("Unable to update the Lucene index", exc);
            }
            return;
        }
        //
        // Save the table update until the update is committed or rolled back.  Note
        // that the current thread is the application thread performing the update operation.
        //
        synchronized (tableUpdates) {
            tableUpdates.add(new TableUpdate(Thread.currentThread(), oldRow, newRow));
        }
        //
        // Register our transaction callback
        //
        H2.H2.registerCallback(this);
    }
    
    @Override
    public void commit() {
        Thread thread = Thread.currentThread();
        try {
            //
            // Update the Lucene index.  Note that a database transaction is associated
            // with a single thread.  So we will commit just those updates generated
            // by the current thread.
            //
            boolean commit = false;
            synchronized (tableUpdates) {
                Iterator<TableUpdate> updateIt = tableUpdates.iterator();
                while (updateIt.hasNext()) {
                    TableUpdate update = updateIt.next();
                    if (update.getThread() == thread) {
                        commitRow(update.getOldRow(), update.getNewRow());
                        updateIt.remove();
                        commit = true;
                    }
                }
            }
            //
            // Commit the index updates
            //
            if (commit) {
                commitIndex();
            }
        } catch (SQLException exc) {
            LoggerUtil.logError("Unable to update the Lucene index", exc);
        }
    }
    
    @Override
    public void rollback() {
        Thread thread = Thread.currentThread();
        synchronized (tableUpdates) {
            Iterator<TableUpdate> it = tableUpdates.iterator();
            while (it.hasNext()) {
                TableUpdate update = it.next();
                if (update.getThread() == thread) {
                    it.remove();
                }
            }
        }
    }
    
}
