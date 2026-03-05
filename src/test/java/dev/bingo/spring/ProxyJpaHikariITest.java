package dev.bingo.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ProxyJpaHikariITest.TestApplication.class)
@Testcontainers
public class ProxyJpaHikariITest {

    @SpringBootApplication
    static class TestApplication {}

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres");

    @Autowired
    private TestRepository repository;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String proxyUrl = postgres.getJdbcUrl().replace("jdbc:", "jdbc:t-proxy:");
        registry.add("spring.datasource.url", () -> proxyUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "dev.bingo.ProxyDriver");
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void testFullStackWithHikariAndJpa() throws Exception {
        assertTrue(dataSource.getClass().getName().contains("HikariDataSource"), "Should be HikariCP");

        try (Connection conn = dataSource.getConnection()) {
            assertTrue(conn.isWrapperFor(Connection.class), "Connection should be wrapped by ProxyDriver");
            assertTrue(conn.isWrapperFor(org.postgresql.PGConnection.class));
        }

        TestEntity entity = new TestEntity();
        entity.data = "Spring Boot 4.0 Prototype";
        repository.save(entity);

        assertNotNull(entity.id);
        assertEquals(1, repository.count());
    }

    @Test
    void testHikariConnectionViaProxy() throws Exception {
        assertNotNull(dataSource);
        assertTrue(dataSource.getConnection().isWrapperFor(org.postgresql.PGConnection.class));
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(conn.isWrapperFor(Connection.class));
            assertFalse(conn.isClosed());
        }
    }
}
