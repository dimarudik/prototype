package dev.bingo.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.bingo.ProxyDriver;
import dev.bingo.provider.URLProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Driver;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ProxyURLProviderITest.TestApplication.class)
@Testcontainers
@DirtiesContext
public class ProxyURLProviderITest {

    private static final Logger log = LoggerFactory.getLogger(ProxyURLProviderITest.class);

    @SpringBootApplication
    static class TestApplication {
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
            sds.setUrl(postgres.getJdbcUrl()
                    .replace("jdbc:", "jdbc:t-proxy:")
                    .replace("localhost", "foo"));
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
    }

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void waitForContainer(DynamicPropertyRegistry registry) {
        postgres.start();
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void testHikariConnectionViaTestURLProviderViaProxy() throws Exception {
        assertNotNull(dataSource);
        assertTrue(dataSource.getConnection().isWrapperFor(org.postgresql.PGConnection.class));
        try (Connection conn = dataSource.getConnection()) {
            String actualUrl = conn.getMetaData().getURL();
            assertTrue(actualUrl.startsWith("jdbc:postgresql://localhost:"));
            assertTrue(conn.isWrapperFor(Connection.class));
            assertFalse(conn.isClosed());
        }
    }
}
