package net.sourceforge.sheltermanager.cursorengine.http;

import net.sourceforge.sheltermanager.dbfs.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

import java.util.ArrayList;


public class HttpStatement implements Statement {
    private String url;
    private Connection c;
    private String basicAuth = null;
    private ArrayList<String> batch = new ArrayList<String>();

    public HttpStatement(Connection c, String url) {
        this.c = c;
        this.url = url;
        extractUserPass();
    }

    /**
     * If the URL has a user:pass@ portion in the host,
     * extract to the username and password fields so
     * we know to use BASIC authentication when requesting
     * data
     */
    private void extractUserPass() {
        if (url.indexOf("@") != -1) {
            String upw = url.substring(url.indexOf("//") + 2, url.indexOf("@"));
            String username = upw.substring(0, upw.indexOf(":"));
            String password = upw.substring(upw.indexOf(":") + 1);
            url = url.substring(0, url.indexOf("//") + 2) +
                url.substring(url.indexOf("@") + 1);
            basicAuth = Base64.encode(username + ":" + password);
        }
    }

    @Override
    public void addBatch(String arg0) throws SQLException {
        batch.add(arg0);
    }

    @Override
    public void cancel() throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void clearBatch() throws SQLException {
        batch.clear();
    }

    @Override
    public void clearWarnings() throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void close() throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void closeOnCompletion() throws SQLException{
        // java7
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return executeUpdate(sql) > 0;
    }

    @Override
    public boolean execute(String sql, int arg1) throws SQLException {
        return executeUpdate(sql) > 0;
    }

    @Override
    public boolean execute(String sql, int[] arg1) throws SQLException {
        return executeUpdate(sql) > 0;
    }

    @Override
    public boolean execute(String sql, String[] arg1) throws SQLException {
        return executeUpdate(sql) > 0;
    }

    @Override
    public int[] executeBatch() throws SQLException {
        StringBuffer b = new StringBuffer();

        for (String s : batch) {
            if (b.length() > 0) {
                b.append(";;");
            }

            b.append(s);
        }

        clearBatch();

        return new int[] { executeUpdate(b.toString()) };
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        try {
            String data = "sql=" + URLEncoder.encode(sql, "UTF-8");

            URL u = new URL(url);
            URLConnection uc = u.openConnection();

            if (basicAuth != null) {
                uc.setRequestProperty("Authorization", "Basic " + basicAuth);
            }

            uc.setDoOutput(true);

            OutputStreamWriter w = new OutputStreamWriter(uc.getOutputStream());
            w.write(data);
            w.flush();

            // Get our response
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                        uc.getInputStream(), "UTF8"));

            ArrayList<String> lines = new ArrayList<String>();
            String line;

            while ((line = rd.readLine()) != null) {
                if (line.startsWith("ERR")) {
                    throw new SQLException(line);
                }

                lines.add(line);
            }

            w.close();
            rd.close();

            return new HttpResultSet(c, url, lines);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException(e);
        }
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        try {
            String data = "sql=" + URLEncoder.encode(sql, "UTF-8");
            int changed = 0;

            URL u = new URL(url);
            URLConnection uc = u.openConnection();

            if (basicAuth != null) {
                uc.setRequestProperty("Authorization", "Basic " + basicAuth);
            }

            uc.setDoOutput(true);

            OutputStreamWriter w = new OutputStreamWriter(uc.getOutputStream());
            w.write(data);
            w.flush();

            // Get our response
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                        uc.getInputStream(), "UTF8"));

            String line;

            while ((line = rd.readLine()) != null) {
                if (line.startsWith("ERR")) {
                    throw new SQLException(line);
                }

                try {
                    changed = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                }
            }

            w.close();
            rd.close();

            return changed;
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException(e);
        }
    }

    @Override
    public int executeUpdate(String sql, int arg1) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, int[] arg1) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, String[] arg1)
        throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return c;
    }

    @Override
    public int getFetchDirection() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getFetchSize() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMaxRows() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean getMoreResults(int arg0) throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getResultSetType() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isClosed() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false; // java7
    }

    @Override
    public boolean isPoolable() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setCursorName(String arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setEscapeProcessing(boolean arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setFetchDirection(int arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setFetchSize(int arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setMaxFieldSize(int arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setMaxRows(int arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setPoolable(boolean arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setQueryTimeout(int arg0) throws SQLException {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }
}
