package org.bsc.langgraph4j.checkpoint;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Wraps a {@link DataSource} and counts connections that were handed out but not closed yet.
 * A pooled data source only gets a connection back when it is closed, so any non-zero count left
 * after a saver call is a connection that the pool would lose.
 */
final class TrackingDataSource implements DataSource {

    private final DataSource delegate;
    private final AtomicInteger open = new AtomicInteger();

    TrackingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    int openConnections() {
        return open.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return track(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return track(delegate.getConnection(username, password));
    }

    private Connection track(Connection connection) {
        open.incrementAndGet();
        var closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close") && closed.compareAndSet(false, true)) {
                        open.decrementAndGet();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
