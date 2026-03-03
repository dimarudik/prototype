package dev.bingo.handler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionInvocationHandler implements InvocationHandler {
    private final Connection connection;
    private static final Logger log = Logger.getLogger("dev.bingo.handler.ConnectionInvocationHandler");

    public ConnectionInvocationHandler(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log.log(Level.FINE, "Method: {0}", method.getName());
        if (method.getName().equals("isWrapperFor")) {
            return args[0].equals(Connection.class) || connection.isWrapperFor((Class<?>) args[0]);
        }
        if (method.getName().equals("unwrap")) {
            if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
            return connection.unwrap((Class<?>) args[0]);
        }
        try {
            Object result = method.invoke(connection, args);
            if (result instanceof Statement) {
                String capturedSql = null;
                if (method.getName().equals("prepareStatement") && args != null && args.length > 0) {
                    capturedSql = (String) args[0];
                }
                return Proxy.newProxyInstance(
                        result.getClass().getClassLoader(),
                        new Class[]{method.getReturnType(), java.sql.Wrapper.class, AutoCloseable.class},
                        new StatementInvocationHandler((Statement) result, capturedSql)
                );
            }
            return result;
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
