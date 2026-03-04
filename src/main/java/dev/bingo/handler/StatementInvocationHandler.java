package dev.bingo.handler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatementInvocationHandler implements InvocationHandler {
    private final Statement target;
    private final String capturedSql;
    private static final Logger log = Logger.getLogger("dev.bingo.handler.StatementInvocationHandler");

    public StatementInvocationHandler(Statement target, String capturedSql) {
        this.target = target;
        this.capturedSql = capturedSql;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Безопасная проверка Wrapper
        if ("isWrapperFor".equals(methodName) && args != null && args.length == 1) {
            Class<?> iface = (Class<?>) args[0];
            return iface.isInstance(proxy) || target.isWrapperFor(iface);
        }
        if ("unwrap".equals(methodName) && args != null && args.length == 1) {
            Class<?> iface = (Class<?>) args[0];
            if (iface.isInstance(proxy)) return proxy;
            return target.unwrap(iface);
        }

        if (methodName.startsWith("execute")) {
            long start = System.currentTimeMillis();
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } finally {
                long duration = System.currentTimeMillis() - start;
                String sqlToLog = (capturedSql != null) ? capturedSql : (args[0].toString());

                log.log(Level.FINE, "Query: {0}. Execution time: {1} ms.", new Object[]{sqlToLog, duration});
            }
        }

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
