package dev.bingo.core;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class ProxyDriverITest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeAll
    static void setup() {
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();
        java.util.logging.LogManager.getLogManager().getLogger("").setLevel(java.util.logging.Level.FINE);
    }

    @Test
    void testProxyDriverIntegration() throws SQLException {
        String proxyUrl = postgres.getJdbcUrl().replace("jdbc:", "jdbc:t-proxy:");

        Properties props = new Properties();
        props.setProperty("user", postgres.getUsername());
        props.setProperty("password", postgres.getPassword());

        try (Connection conn = DriverManager.getConnection(proxyUrl, props)) {

            assertNotNull(conn, "Connection should not be null");
            assertTrue(conn.isWrapperFor(Connection.class), "Connection should be wrapped by ProxyDriver");

            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT current_database(), 123 as secret_code");

                assertTrue(rs.next());
                assertEquals("testdb", rs.getString(1));
                assertEquals(123, rs.getInt("secret_code"));
            }
        }
    }

    @Test
    void testPreparedStatementProxy() throws SQLException {
        String proxyUrl = postgres.getJdbcUrl().replace("jdbc:", "jdbc:t-proxy:");

        try (Connection conn = DriverManager.getConnection(proxyUrl, postgres.getUsername(), postgres.getPassword())) {
            String sql = "SELECT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, 42);
                ResultSet rs = pstmt.executeQuery();
                assertTrue(rs.next());
                assertEquals(42, rs.getInt(1));
            }
        }
    }
}
