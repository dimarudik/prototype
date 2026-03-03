package dev.bingo;

import dev.bingo.handler.ConnectionInvocationHandler;
import dev.bingo.provider.DefaultURLProvider;
import dev.bingo.provider.URLProvider;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProxyDriver implements Driver {
    private volatile URLProvider urlProvider;
    private static ProxyDriver proxyDriver;
    private static final Logger log = Logger.getLogger("dev.bingo.ProxyDriver");

    static {
        try {
            proxyDriver = new ProxyDriver();
            DriverManager.registerDriver(proxyDriver);
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to register driver", e);
        }
    }

    public ProxyDriver() {
        this.urlProvider = new DefaultURLProvider();
    }

    public static ProxyDriver getProxyDriver() {
        return proxyDriver;
    }

    public URLProvider getUrlProvider() {
        return urlProvider;
    }

    public void setUrlProvider(URLProvider urlProvider) {
        this.urlProvider = urlProvider;
    }

    @Override
    public Connection connect(String url, Properties properties) throws SQLException {
        if (!acceptsURL(url)) return null;
        String realUrl = urlProvider.getURL(url);
        log.log(Level.FINE, "Real URL: {0}", realUrl);
        properties.forEach((k, v) -> log.log(Level.FINE, "{0} = {1}", new Object[]{k, v}));
        try {
            if (!acceptsURL(realUrl)) {
                Class.forName("org.postgresql.Driver");
                Connection realConn = DriverManager.getConnection(realUrl, properties);
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class[]{Connection.class, Wrapper.class, AutoCloseable.class},
                        new ConnectionInvocationHandler(realConn));
            } else {
                throw new SQLException("URL is not supported");
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return urlProvider.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
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
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
}
