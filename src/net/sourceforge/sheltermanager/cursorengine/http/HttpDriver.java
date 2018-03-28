package net.sourceforge.sheltermanager.cursorengine.http;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;

import java.util.Properties;
import java.util.logging.Logger;


public class HttpDriver implements Driver {
    static {
        try {
            // Register the Driver with DriverManager
            HttpDriver inst = new HttpDriver();
            DriverManager.registerDriver(inst);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean acceptsURL(String arg0) throws SQLException {
        return arg0.startsWith("jdbc:http");
    }

    @Override
    public Connection connect(String arg0, Properties arg1)
        throws SQLException {
        if (arg0.startsWith("jdbc:http")) {
            HttpConnection c = new HttpConnection(arg0);
            // By asking for the product name, we're making a request
            // of the server and testing this connection is valid
            c.getMetaData().getDatabaseProductName();

            return c;
        }

        throw new SQLException("Invalid URL: " + arg0);
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public Logger getParentLogger() {
        return null; // java7
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String arg0, Properties arg1)
        throws SQLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }
}
