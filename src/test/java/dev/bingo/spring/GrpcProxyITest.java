package dev.bingo.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.bingo.HostProvider;
import dev.bingo.HostServiceGrpc;
import dev.bingo.ProxyDriver;
import dev.bingo.provider.GrpcURLProvider;
import dev.bingo.provider.URLProvider;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = GrpcProxyITest.GrpcTestApplication.class,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
@DirtiesContext
public class GrpcProxyITest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres");
    static Server grpcServer;
    static int grpcPort;

    static {
        postgres.start();
        try {
            grpcPort = 9090;
            grpcServer = ServerBuilder.forPort(grpcPort)
                    .addService(new HostServiceImpl(postgres.getHost() + ":" + postgres.getFirstMappedPort()))
                    .build().start();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    @Autowired
    private DataSource dataSource;

    @SpringBootApplication(scanBasePackages = "none")
    static class GrpcTestApplication {

        @Bean("grpcURLProvider")
        @Primary
        public URLProvider grpcURLProvider() {
            return new GrpcURLProvider();
        }

        @Bean
        public DataSource dataSource(@Qualifier("grpcURLProvider")URLProvider urlProvider) {
            ProxyDriver driver = new ProxyDriver();
            driver.setUrlProvider(urlProvider);
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:t-proxy:postgresql://localhost:" + grpcPort + "/" + postgres.getDatabaseName());
            config.setDriverClassName("dev.bingo.ProxyDriver");
            config.setDataSource(new SimpleDriverDataSource(driver, config.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            return new HikariDataSource(config);
        }
    }

    static class HostServiceImpl extends HostServiceGrpc.HostServiceImplBase {
        private final String realPgAddress;
        HostServiceImpl(String pgAddress) { this.realPgAddress = pgAddress; }

        @Override
        public void getHosts(HostProvider.HostRequest req, StreamObserver<HostProvider.HostResponse> responseObserver) {
            responseObserver.onNext(HostProvider.HostResponse.newBuilder().addHosts(realPgAddress).build());
            responseObserver.onCompleted();
        }
    }

    @Test
    void testGrpcDiscoveryAndConnect() throws Exception {
        assertNotNull(dataSource);
        try (Connection conn = dataSource.getConnection()) {
            String actualUrl = conn.getMetaData().getURL();
            assertTrue(actualUrl.startsWith("jdbc:postgresql://localhost:"));
            assertTrue(actualUrl.contains(String.valueOf(postgres.getFirstMappedPort())));
            assertFalse(conn.isClosed());
        }
    }

    @AfterAll
    static void stop() { if (grpcServer != null) grpcServer.shutdown(); }
}
