package dev.bingo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
public class ProxyDriverExceptionITest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres");

    @BeforeAll
    static void setup() {
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINE);
    }

    @Test
    void testSqlExceptionPropagatesCorrectly() throws SQLException {
        String proxyUrl = postgres.getJdbcUrl().replace("jdbc:", "jdbc:t-proxy:");
        try (Connection conn = DriverManager.getConnection(proxyUrl, postgres.getUsername(), postgres.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                SQLException exception = assertThrows(SQLException.class, () -> stmt.executeQuery("SELECT * FROM non_existent_table_123"));
                assertEquals("42P01", exception.getSQLState());
                System.out.println("Captured expected exception: " + exception.getMessage());
            }
        }
    }

    @Test
    void testPreparedStatementExceptionPropagates() throws SQLException {
        String proxyUrl = postgres.getJdbcUrl().replace("jdbc:", "jdbc:t-proxy:");
        try (Connection conn = DriverManager.getConnection(proxyUrl, postgres.getUsername(), postgres.getPassword())) {
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE test_table (id INTEGER)");
            }
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO test_table(id) VALUES (?)")) {
                pstmt.setString(1, "not_an_integer");
                SQLException exception = assertThrows(SQLException.class, () -> pstmt.executeUpdate());
                assertEquals("42804", exception.getSQLState());
                System.out.println("Captured expected type error: " + exception.getMessage());
            }
        }
    }
}
