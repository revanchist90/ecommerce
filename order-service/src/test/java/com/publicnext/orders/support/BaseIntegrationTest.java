package com.publicnext.orders.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", TestPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", TestPostgresContainer.INSTANCE::getPassword);
        registry.add("orders.auto-progression.enabled", () -> "false");
    }
}
