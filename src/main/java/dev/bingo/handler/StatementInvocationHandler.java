package dev.bingo.handler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatementInvocationHandler implements InvocationHandler {
    private final Statement target;
    private final String preparedSql;
    private static final Logger log = Logger.getLogger("dev.bingo.handler.StatementInvocationHandler");

    public StatementInvocationHandler(Statement target, String preparedSql) {
        this.target = target;
        this.preparedSql = preparedSql;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // 1. Логика Wrapper: позволяем инструменту мониторинга добраться до реального Statement
        if ("isWrapperFor".equals(methodName) && args.length == 1) {
            Class<?> iface = (Class<?>) args[0];
            return iface.isInstance(proxy) || iface.isInstance(target);
        }

        if ("unwrap".equals(methodName) && args.length == 1) {
            Class<?> iface = (Class<?>) args[0];
            if (iface.isInstance(proxy)) return proxy;
            if (iface.isInstance(target)) return target;
        }

        // 2. Логика замера времени
        if (methodName.startsWith("execute")) {
            long start = System.currentTimeMillis();
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause(); // Важно для проброса SQLException
            } finally {
                long duration = System.currentTimeMillis() - start;
                // Логируем результат
                String sqlToLog;
                if (preparedSql != null) {
                    // Это PreparedStatement
                    sqlToLog = preparedSql;
                } else if (args != null && args.length > 0 && args[0] instanceof String) {
                    // Это обычный Statement.execute(String sql)
                    sqlToLog = (String) args[0];
                } else {
                    sqlToLog = "Internal/Batch query";
                }
                log.log(Level.FINE, "Query: {0}. Execution time: {1} ms.", new Object[]{sqlToLog, duration / 1_000_000.0});
            }
        }

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
