package dev.bingo.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.bingo.ProxyDriver;
import dev.bingo.provider.URLProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Driver;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ProxyURLProviderITest.TestApplication.class)
@Testcontainers
public class ProxyURLProviderITest {

    @SpringBootApplication
    static class TestApplication {}

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres");

    @Autowired
    private TestRepository repository;

    @Autowired
    private DataSource dataSource;

    @Bean
    public Driver jdbcDriver(URLProvider urlProvider) {
        ProxyDriver driver = new ProxyDriver();
        driver.setUrlProvider(urlProvider);
        return driver;
    }

    @Bean
    public DataSource dataSource(Driver jdbcDriver) {
        SimpleDriverDataSource sds = new SimpleDriverDataSource();
        sds.setDriver(jdbcDriver);
//        sds.setUrl("jdbc:t-proxy:postgresql://foo:5432/postgres");
        sds.setUrl(postgres.getJdbcUrl().replace("jdbc:", "jdbc:t-proxy:"));
        sds.setUsername(postgres.getUsername());
        sds.setPassword(postgres.getPassword());
        HikariConfig config = new HikariConfig();
        config.setDataSource(sds);
        return new HikariDataSource(config);
    }

    @Bean
    public URLProvider newURLProvider() {
        return new TestURLProvider();
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
