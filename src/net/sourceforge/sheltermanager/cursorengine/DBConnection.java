/**
 *
 *  SQLRecordset Disconnected Recordset functionality
 *  and client-side cursor engine.
 *
 *  Copyright (C) 2002-2010  Robin Rawson-Tetley
 *
 *  www.rawsontetley.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *
 *  <robin@rawsontetley.org>
  */
package net.sourceforge.sheltermanager.cursorengine;

import net.sourceforge.sheltermanager.asm.globals.Global;
import net.sourceforge.sheltermanager.asm.utility.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import java.sql.*;

import java.util.ArrayList;


/**
 * Handles JDBC elements of the client-side RTDS cursor engine.
 *
 * @see net.sourceforge.sheltermanager.cursorengine.DBConnection
 * @see net.sourceforge.sheltermanager.cursorengine.SQLRowData
 * @see net.sourceforge.sheltermanager.cursorengine.SQLFieldDescriptor
 *
 * @author Robin Rawson-Tetley
 * @version 1.0
 */
public abstract class DBConnection {
    /**
     * The connection member - this should only be set once on first use and
     * then accessed subsequently
     */
    public static Connection con = null;

    /** The last error we received trying to connect */
    public static String lastError = "";

    /**
     * The JDBC URL for connecting to the database
     */
    public static String url = "";

    /**
     * Whether or not to print SQL debug info to console
     */
    public static boolean quiet = true;
    public final static byte MYSQL = 0;
    public final static byte POSTGRESQL = 1;
    public final static byte HSQLDB = 2;
    public final static byte HTTP = 3;

    // Types only accessible through a bridge (HTTP for now)
    // because we don't have a direct Java driver
    public final static byte SQLITE = 4;

    /** The database type (inferred from JDBC URL given) */
    public static byte DBType = MYSQL;

    /** The storage type - typically the same as DBType, unless
      * DBType == HTTP, in which case DBStoreType gives us the
      * real database type
      */
    public static byte DBStoreType = MYSQL;

    /** True if the database is in read only mode */
    public static boolean readOnly = false;

    public static void loadJDBCDrivers() {
        loadJDBCDrivers(true, true, true);
    }

    public static void loadJDBCDrivers(boolean postgres, boolean mysql,
        boolean hsql) {
        if (mysql) {
            Global.logInfo("MySQL driver...", "DBConnection.loadJDBCDrivers");

            try {
                // Load the JDBC drivers
                // String url = "jdbc:mysql://localhost/asm?user=root"
                Class.forName("org.gjt.mm.mysql.Driver");
            } catch (Exception e) {
                Global.logException(e, DBConnection.class);
            }
        }

        if (postgres) {
            Global.logInfo("PostgreSQL driver...",
                "DBConnection.loadJDBCDrivers");

            try {
                // String url =
                // "jdbc:postgresql://localhost/test?user=fred&password=secret";
                Class.forName("org.postgresql.Driver");
            } catch (Exception e) {
                Global.logException(e, DBConnection.class);
            }
        }

        if (hsql) {
            Global.logInfo("HSQL driver...", "DBConnection.loadJDBCDrivers");

            try {
                // String url = "jdbc:hsqldb:file:asm"
                Class.forName("org.hsqldb.jdbcDriver");
            } catch (Exception e) {
                Global.logException(e, DBConnection.class);
            }
        }

        // Always load our built in Http driver since
        // its part of our codebase and will always be present
        // Also, load it last as we've had issues with it trying
        // to take URLs for other databases (although it's much
        // more choosy now)
        try {
            // String url = "jdbc:http://localhost/httpdb.cgi"
            Class.forName(
                "net.sourceforge.sheltermanager.cursorengine.http.HttpDriver");
        } catch (Exception e) {
            Global.logException(e, DBConnection.class);
        }
    }

    /** Returns type@host */
    public static String getDBInfo() {
        String type = "";

        if (DBType == HSQLDB) {
            type = "hsqldb";
        }

        if (DBType == MYSQL) {
            type = "mysql";
        }

        if (DBType == POSTGRESQL) {
            type = "postgresql";
        }

        if (DBType == HTTP) {
            type = "http";

            if (DBStoreType == MYSQL) {
                type += "->mysql";
            } else if (DBStoreType == POSTGRESQL) {
                type += "->postgresql";
            } else if (DBStoreType == SQLITE) {
                type += "->sqlite";
            } else if (DBStoreType == HSQLDB) {
                type += "->hsqldb";
            }
        }

        String host = "local";
        int dp = url.indexOf("//");

        if (dp != -1) {
            int sp = url.indexOf("/", dp + 2);

            if (sp != -1) {
                host = url.substring(dp + 2, sp);
            }
        }

        if (host.indexOf("@") != -1) {
            host = host.substring(host.indexOf("@") + 1);
        }

        return type + "@" + host;
    }

    /** Opens a Connection for the URL given */
    public static synchronized Connection getConnection(String url) {
        try {
            Global.logDebug("Getting connection: " + url,
                "DBConnection.getConnection");

            return DriverManager.getConnection(url);
        } catch (Exception e) {
            Global.logDebug("Failed: " + url, "DBConnection.getConnection");
            Global.logException(e, DBConnection.class);

            return null;
        }
    }

    public static synchronized void testConnection(String url)
        throws Exception {
        Connection c = DriverManager.getConnection(url);
        c.close();
    }

    /** Closes any connection we have open */
    public static synchronized void close() {
        try {
            if (con != null) {
                con.close();
            }

            con = null;

            // Hack - HSQLDB 1.8.0 doesn't release locks straight away
            if (DBType == HSQLDB) {
                org.hsqldb.DatabaseManager.closeDatabases(0);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Checks to see if the global connection has been set and if not, sets it.
     * Returns false if the connection is bad.
     */
    public static synchronized boolean getConnection() {
        try {
            // Don't reconnect if we already have a connection
            if ((con != null) && !con.isClosed()) {
                return true;
            }

            DBType = getDBTypeForUrl(url);
            DBStoreType = DBType;
            con = DriverManager.getConnection(url);

            // For indirect connections, figure out the real type
            if (DBType == HTTP) {
                DBStoreType = getDBStoreType();
            }

            DatabaseMetaData dma = con.getMetaData();

            if (!quiet) {
                Global.logInfo("Connected to " + dma.getURL(),
                    "DBConnection.getConnection");
                Global.logInfo("Driver    " + dma.getDriverName(),
                    "DBConnection.getConnection");
                Global.logInfo("Version   " + dma.getDriverVersion(),
                    "DBConnection.getConnection");
            }
        } catch (java.lang.Exception ex) {
            Global.logException(ex, DBConnection.class);
            lastError = ex.getMessage();

            return false;
        }

        return true;
    }

    /** If the database supports checkpoints, issue one */
    public synchronized static void checkpoint() {
        if (DBType == HSQLDB) {
            Global.logDebug("Issuing checkpoint.", "DBConnection.checkpoint");

            try {
                executeAction("CHECKPOINT");
            } catch (Exception e) {
                Global.logException(e, DBConnection.class);
            }
        }
    }

    public static byte getDBTypeForUrl(String url) {
        // Look for db specific bits of JDBC URL to
        // update the DBType flag
        if (url.indexOf("mysql") != -1) {
            return MYSQL;
        }

        if (url.indexOf("postgresql") != -1) {
            return POSTGRESQL;
        }

        if (url.indexOf("hsqldb") != -1) {
            return HSQLDB;
        }

        if (url.indexOf("http") != -1) {
            return HTTP;
        }

        return MYSQL;
    }

    /**
     * Gets the backing DB type for indirect connections
     */
    public static byte getDBStoreType() {
        try {
            String db = con.getMetaData().getDatabaseProductName();

            if (db.equals("POSTGRESQL")) {
                return POSTGRESQL;
            }

            if (db.equals("MYSQL")) {
                return MYSQL;
            }

            if (db.equals("HSQLDB")) {
                return HSQLDB;
            }

            if (db.equals("SQLITE")) {
                return SQLITE;
            }

            throw new Exception("UKNOWN DATABASE TYPE: " + db);
        } catch (Exception e) {
            Global.logException(e, DBConnection.class);

            return MYSQL;
        }
    }

    /**
     * Overloaded version to use the default connection
     *
     * @param stmt
     * @param query
     * @return
     * @throws Exception
     */
    public synchronized static ResultSet openResultset(Statement stmt,
        String query) throws Exception {
        getConnection();

        return openResultset(con, stmt, query);
    }

    /**
     * Pass in a resultset and statement pointer with a query and this routine
     * will populate the resultset for you from the DB.
     *
     * @param c
     *            A JDBC connection
     * @param stmt
     *            A valid statement pointer (see getStatement())
     * @param query
     *            Some SQL to execute to use as the source.
     * @return A populated JDBC resultset.
     */
    public synchronized static ResultSet openResultset(Connection c,
        Statement stmt, String query) throws Exception {
        // Create a new statement and run the query
        stmt = c.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY);

        if (!quiet) {
            Global.logDebug(query, "DBConnection.openResultset");
        }

        return stmt.executeQuery(query);
    }

    /**
     * Call this routine to obtain a new statement for use with obtaining a
     * resultset
     *
     * @return A new statement, generated from the database connection.
     */
    static synchronized Statement getStatement() throws Exception {
        getConnection();

        return getStatement(con);
    }

    static synchronized Statement getStatement(Connection c)
        throws Exception {
        return c.createStatement();
    }

    /**
     * Call this routine with a piece of valid SQL to have it executed against
     * the database connnection.
     */
    public synchronized static void executeAction(String query)
        throws Exception {
        getConnection();
        executeAction(con, query);
    }

    /**
     * Call this routine with a batch of valid SQL to have them executed against
     * the database connnection.
     */
    public synchronized static void executeAction(ArrayList<String> batch)
        throws Exception {
        getConnection();
        executeAction(con, batch);
    }

    /**
     * Call this routine with a piece of valid SQL to have it executed against
     * the database connnection.
     */
    public synchronized static int executeUpdate(String query)
        throws Exception {
        getConnection();

        return executeUpdate(con, query);
    }

    /**
     * Overloaded executeAction that allows the passing of a Connection
     *
     * @param query
     * @throws Exception
     */
    public synchronized static void executeAction(Connection c, String query)
        throws Exception {
        // System.out.println("Executing: " + query);
        // Make sure we have a connection open
        // Create a new statement and run it
        Statement stmt = c.createStatement();

        if (!quiet) {
            Global.logDebug(query, "DBConnection.executeAction");
        }

        if (!readOnly) {
            stmt.execute(query);
        }
    }

    /**
     * Overloaded executeAction that allows the passing of a Connection
     *
     * @param query
     * @throws Exception
     */
    public synchronized static void executeAction(Connection c,
        ArrayList<String> batch) throws Exception {
        Statement stmt = c.createStatement();

        if (!quiet) {
            for (String s : batch) {
                Global.logDebug("BATCH: " + s, "DBConnection.executeAction");
            }
        }

        for (String s : batch) {
            stmt.addBatch(s);
        }

        if (!readOnly) {
            stmt.executeBatch();
        }
    }

    /**
     * Overloaded executeUpdate that allows the passing of a Connection
     *
     * @param query
     * @throws Exception
     */
    public synchronized static int executeUpdate(Connection c, String query)
        throws Exception {
        // System.out.println("Executing: " + query);
        // Make sure we have a connection open
        // Create a new statement and run it
        Statement stmt = c.createStatement();

        if (!quiet) {
            Global.logDebug(query, "DBConnection.executeAction");
        }

        if (!readOnly) {
            return stmt.executeUpdate(query);
        } else {
            return 0;
        }
    }

    public synchronized static void executeFile(File f)
        throws Exception {
        getConnection();
        executeFile(con, f);
    }

    public synchronized static void executeResource(String s)
        throws Exception {
        getConnection();
        executeResource(con, s);
    }

    /**
     * Executes a sum query and returns a double of the result
     * @param sql
     * @return A double containing the result, or 0 if the result
     *         was null.
     */
    public synchronized static double executeForSum(String sql)
        throws Exception {
        return executeForSum(con, sql);
    }

    public synchronized static double executeForDouble(String sql)
        throws Exception {
        return executeForSum(sql);
    }

    public synchronized static String executeForString(String sql)
        throws Exception {
        return executeForString(con, sql);
    }

    public synchronized static java.util.Date executeForDate(String sql)
        throws Exception {
        return executeForDate(con, sql);
    }

    /**
     * Executes a sum query and returns a double of the result
     * @param c The connection
     * @param sql
     * @return A double containing the result, or 0 if the result
     *         was null.
     */
    public synchronized static double executeForSum(Connection c, String sql)
        throws Exception {
        Statement stmt = c.createStatement();
        ResultSet r = stmt.executeQuery(sql);

        if (r.next()) {
            return r.getDouble(1);
        } else {
            return 0;
        }
    }

    /**
     * Executes a query and returns a string of the first result
     * @param c The connection
     * @param sql
     * @return A string containing the result, or "" if the result was null
     */
    public synchronized static String executeForString(Connection c, String sql)
        throws Exception {
        Statement stmt = c.createStatement();
        ResultSet r = stmt.executeQuery(sql);

        if (r.next()) {
            return r.getString(1);
        } else {
            return "";
        }
    }

    /**
     * Executes a query and returns a date of the first result
     * @param c The connection
     * @param sql
     * @return A date containing the result, or null if the result was null
     */
    public synchronized static java.util.Date executeForDate(Connection c,
        String sql) throws Exception {
        Statement stmt = c.createStatement();
        ResultSet r = stmt.executeQuery(sql);

        if (r.next()) {
            Timestamp t = r.getTimestamp(1);

            return new java.util.Date(t.getTime());
        } else {
            return null;
        }
    }

    /**
     * Executes a count query and returns an integer of the result
     * @param c The connection
     * @param sql
     * @return An integer containing the result, or 0 if the result
     *         was null.
     */
    public synchronized static int executeForCount(Connection c, String sql)
        throws Exception {
        Statement stmt = c.createStatement();
        ResultSet r = stmt.executeQuery(sql);

        if (r.next()) {
            return r.getInt(1);
        } else {
            return 0;
        }
    }

    /**
     * Executes a count query and returns an integer of the result
     * overload to use default connection
     * @param sql
     * @return An integer containing the result, or 0 if the result
     *         was null.
     */
    public synchronized static int executeForCount(String sql)
        throws Exception {
        getConnection();

        return executeForCount(con, sql);
    }

    public synchronized static int executeForInt(String sql)
        throws Exception {
        return executeForCount(sql);
    }

    /**
     * Executes the contents of a file against the database
     *
     * @param c
     * @param f
     * @throws Exception
     */
    public synchronized static void executeFile(Connection c, File f)
        throws Exception {
        // Read file into string
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        in.read(b);
        in.close();
        executeBatchFile(c, b);
    }

    /**
     * Reads a java resource file and executes it
     *
     * @param c The connection
     * @param s name of the classpath resource
     * @return
     * @throws IOException
     */
    public synchronized static void executeResource(Connection c, String s)
        throws Exception {
        InputStream in = DBConnection.class.getResourceAsStream(s);

        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);

        in.close();
        out.close();

        getConnection();
        executeBatchFile(con, out.toByteArray());
    }

    /**
     * Executes a byte array containing file contents against the db
     *
     * @param c
     * @param b
     * @throws Exception
     */
    public synchronized static void executeBatchFile(Connection c, byte[] b)
        throws Exception {
        // Interpret the file 
        String s = new String(b, Global.CHAR_ENCODING);

        // If we have unicode escape sequences in there, reinterpret
        // the file as plain ASCII instead so that the characters
        // are substitued
        if (s.indexOf("\\u") != -1) {
            Global.logDebug("Found ASCII unicode escape sequences in " +
                Global.CHAR_ENCODING + " data, substituting",
                "DBConnection.executeBatchFile");
            s = Utils.unescapeUnicode(s);
        }

        // Find the first query in the file data
        int be = 0;
        int en = s.indexOf(";\n");

        // If we have a byte order marker, skip to the next char
        // (because java signs all bytes, then < 0 means we're really 
        // testing that the first byte is > 127 and outside ascii range)
        if (b[0] < 0) {
            Global.logDebug("Found Unicode Byte-Order-Marker, skipping",
                "DBConnection.executeBatchFile");
            be++;
        }

        while (true) {
            String sql = s.substring(be, en).trim();
            Global.logDebug("FileQuery: " + sql, "DBConnection.executeBatchFile");

            try {
                executeAction(c, sql);
            }
            catch (Exception e) {
                // Log and skip errors
                Global.logException(e, DBConnection.class);
            }

            be = en + 1;
            en = s.indexOf(";\n", be);

            if (en == -1) {
                break;
            }
        }
    }

    /**
     * Writes INSERT statements to regenerate the named table
     * and returns them as a string - the inserts are full with
     * fieldnames reiterated.
     */
    public static String dumpTable(String tableName) {
        try {
            SQLRecordset r = new SQLRecordset();
            r.openRecordset("SELECT * FROM " + tableName, tableName);

            return r.dump();
        } catch (Exception e) {
            Global.logException(e, DBConnection.class);
        }

        return "";
    }

    /**
     * Given a target connection, dbType and tableName, deletes the contents of
     * the table in the target database then copies all the data from the
     * current db to the target table
     *
     * @param target
     *            The target database connection
     * @param dbType
     *            MYSQL, POSTGRESQL or HSQLDB
     * @param tableName
     *            The table to copy
     */
    public static void copyTable(Connection target, byte dbType,
        String tableName, boolean hasUserInfo, String userName) {
        try {
            Global.logInfo("Copying: " + tableName, "DBConnection.copyTable");

            // Drop everything from the target table
            executeAction(target, "DELETE FROM " + tableName);

            // Read everything from the source table
            SQLRecordset r = new SQLRecordset();
            r.openRecordset("SELECT * FROM " + tableName, tableName);

            // Don't do anything if we have no records
            if (r.getEOF()) {
                return;
            }

            // Write this recordset out to a our target
            r.markAllRowsAsNew();
            r.save(target, dbType, hasUserInfo, userName, true);

            // Clean up
            r.free();
            r = null;
        } catch (Exception e) {
            Global.logError("Error occurred copying table: " + e.getMessage(),
                "DBConnection.copyTable");
            Global.logError("DBType=" + dbType + ",Table=" + tableName +
                ",userName=" + userName, "DBConnection.copyTable");
            Global.logException(e, DBConnection.class);
        }
    }

    /**
     * Given a source connection, dbType and tableName, copies
     * all the data from the source db to the current db
     *
     * @param target
     *            The target database connection
     * @param dbType
     *            MYSQL, POSTGRESQL or HSQLDB
     * @param tableName
     *            The table to copy
     */
    public static void importTable(Connection source, byte dbType,
        String tableName, boolean hasUserInfo, String userName,
        boolean breakOnException) {
        try {
            Global.logInfo("Importing: " + tableName, "DBConnection.importTable");

            // Read everything from the source table
            SQLRecordset r = new SQLRecordset();
            r.openRecordset(source, "SELECT * FROM " + tableName, tableName);

            // Don't do anything if we have no records
            if (r.getEOF()) {
                return;
            }

            // Write this recordset out to a our source
            r.markAllRowsAsNew();
            r.save(DBConnection.con, DBConnection.DBType, hasUserInfo,
                userName, breakOnException);

            // Clean up
            r.free();
            r = null;
        } catch (Exception e) {
            Global.logError("Error occurred importing table: " +
                e.getMessage(), "DBConnection.importTable");
            Global.logError("DBType=" + dbType + ",Table=" + tableName +
                ",userName=" + userName, "DBConnection.importTable");
            Global.logException(e, DBConnection.class);
        }
    }

    /**
     * Copies the DBFS from the current database to the target. Handled separately
     * because of the large BLOB fields - don't want to cache the whole table in
     * RAM while we do it.
     *
     * @param target
     */
    public static void copyDBFS(Connection target) {
        try {
            // Clear down the target
            executeAction(target, "DELETE FROM dbfs");

            // Pull out all records less content
            SQLRecordset r = new SQLRecordset();
            r.openRecordset("SELECT ID, Name, Path FROM dbfs", "dbfs");

            // Go through each row from the DBFS, look it up
            // with it's content and flush out to the new database
            if (Global.mainForm != null) {
                Global.mainForm.initStatusBarMax((int) r.getRecordCount());
            }

            while (!r.getEOF()) {
                SQLRecordset r2 = new SQLRecordset();
                Global.logDebug("Copy DBFS Entry: " +
                    r.getField("Path").toString() + "/" +
                    r.getField("Name").toString(), "DBConnection.copyDBFS");
                r2.openRecordset("SELECT * FROM dbfs WHERE ID=" +
                    r.getField("ID").toString(), "dbfs");
                r2.markAllRowsAsNew();
                r2.save(target, DBConnection.HSQLDB, false, "", false);
                r2.free();
                r2 = null;
                r.moveNext();

                if (Global.mainForm != null) {
                    Global.mainForm.incrementStatusBar();
                }
            }

            if (Global.mainForm != null) {
                Global.mainForm.resetStatusBar();
            }
        } catch (Exception e) {
            Global.logError("Error occurred copying table: " + e.getMessage(),
                "DBConnection.copyTable");
            Global.logException(e, DBConnection.class);
        }
    }

    /**
     * Imports the DBFS from the source database to the local one. Due to the
     * nature of DBFS, clears down the current db before the import.
     * Handled separately because of the large BLOB fields - don't want to cache the
     * whole table in RAM while we do it.
     *
     * @param target
     */
    public static void importDBFS(Connection source) {
        try {
            // Clear down the target
            executeAction("DELETE FROM dbfs");

            // Get all records less content
            SQLRecordset r = new SQLRecordset();
            r.openRecordset(source, "SELECT ID, Name, Path FROM dbfs", "dbfs");

            // Go through each row from the DBFS, look it up
            // with it's content and flush out to the new database
            if (Global.mainForm != null) {
                Global.mainForm.initStatusBarMax((int) r.getRecordCount());
            }

            while (!r.getEOF()) {
                SQLRecordset r2 = new SQLRecordset();
                Global.logDebug("Import DBFS Entry: " +
                    r.getField("Path").toString() + "/" +
                    r.getField("Name").toString(), "DBConnection.importDBFS");
                r2.openRecordset(source,
                    "SELECT * FROM dbfs WHERE ID=" +
                    r.getField("ID").toString(), "dbfs");
                r2.markAllRowsAsNew();
                r2.save(DBConnection.con, DBConnection.HSQLDB, false, "", false);
                r2.free();
                r2 = null;
                r.moveNext();

                if (Global.mainForm != null) {
                    Global.mainForm.incrementStatusBar();
                }
            }

            if (Global.mainForm != null) {
                Global.mainForm.resetStatusBar();
            }
        } catch (Exception e) {
            Global.logError("Error occurred copying table: " + e.getMessage(),
                "DBConnection.copyTable");
            Global.logException(e, DBConnection.class);
        }
    }

    /** Returns the next primary key for a given table on the current connection */
    public static int getPrimaryKey(String tableName)
        throws CursorEngineException {
        return getPrimaryKey(con, tableName);
    }

    /**
     * This routine uses the PrimaryKey table which must be present in the
     * database to calculate the next available primary key.
     */
    public synchronized static int getPrimaryKey(Connection c, String tableName)
        throws CursorEngineException {
        // Check the table first for a key value:
        SQLRecordset rs = new SQLRecordset();

        try {
            rs.openRecordset(c,
                "Select * From primarykey Where TableName Like '" + tableName +
                "'", "primarykey");
        } catch (Exception e) {
            throw new CursorEngineException(e.getMessage());
        }

        try {
            if (rs.getBOF()) {
                // No entry in the table found, check the physical
                // table to see what the highest ID in it is:
                SQLRecordset thetable = new SQLRecordset();

                try {
                    thetable.openRecordset(c,
                        "Select ID From " + tableName + " Order By ID",
                        tableName);
                } catch (Exception e) {
                    throw new CursorEngineException(e.getMessage());
                }

                // If there are no records in the table, start at 1
                // and set the table entry to 2 for future use.
                if (thetable.getBOF()) {
                    // Write insert query
                    String sql = "INSERT INTO primarykey (TableName, NextID) VALUES ('" +
                        tableName + "', 2)";

                    try {
                        executeAction(c, sql);
                    } catch (Exception e) {
                        throw new CursorEngineException(e.getMessage());
                    }

                    return 1;
                } else {
                    // Find the highest ID in the table and use that instead
                    thetable.moveLast();

                    int realID = ((Integer) thetable.getField("ID")).intValue();
                    realID++;

                    // Write insert query
                    realID++;

                    String sql = "INSERT INTO primarykey (TableName, NextID) VALUES ('" +
                        tableName + "', " + Integer.toString((realID)) + ")";

                    try {
                        executeAction(c, sql);
                    } catch (Exception e) {
                        throw new CursorEngineException(e.getMessage());
                    }

                    realID--;

                    return realID;
                }
            } else {
                // We have an entry, return that and increment the counter
                int realID = ((Integer) rs.getField("NextID")).intValue();
                int nextID = realID;
                nextID++;

                // Write an update query and run it
                String sql = "UPDATE primarykey SET NextID = " +
                    Integer.toString(nextID) + " WHERE TableName Like '" +
                    tableName + "'";

                try {
                    executeAction(c, sql);
                } catch (Exception e) {
                    throw new CursorEngineException(e.getMessage());
                }

                return realID;
            }
        } finally {
            rs = null;
        }
    }
}
