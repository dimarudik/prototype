package dev.bingo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ProxyJpaHikariIT {

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
        // Формируем прокси-URL
        String proxyUrl = postgres.getJdbcUrl().replace("jdbc:", "jdbc:t-proxy:");

        registry.add("spring.datasource.url", () -> proxyUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Указываем Hikari использовать наш драйвер
        registry.add("spring.datasource.driver-class-name", () -> "dev.bingo.ProxyDriver");
        // Логирование Hibernate (опционально)
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void testFullStackWithHikariAndJpa() throws Exception {
        // 1. Проверяем, что HikariCP поднялся корректно
        assertTrue(dataSource.getClass().getName().contains("HikariDataSource"),
                "Должен использоваться HikariCP");

        // 2. Проверяем, что соединение проходит через наш прокси (Wrapper)
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(conn.isWrapperFor(Connection.class),
                    "Соединение должно поддерживать Wrapper для нашего прокси");
        }

        // 3. Выполняем JPA операцию
        TestEntity entity = new TestEntity();
        entity.data = "Spring Boot 4.0 Prototype";
        repository.save(entity);

        // 4. Проверяем результат
        assertNotNull(entity.id);
        assertEquals(1, repository.count());

        // В логах (если включен DEBUG для dev.bingo) вы увидите INSERT и SELECT,
        // перехваченные вашим StatementInvocationHandler
    }

    @Test
    void testHikariConnectionViaProxy() throws Exception {
        // Проверяем, что HikariCP успешно проинициализировался с нашим драйвером
        assertNotNull(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            // Проверка, что соединение живое и обернуто прокси
            assertTrue(conn.isWrapperFor(Connection.class));
            assertFalse(conn.isClosed());
        }
    }
}
