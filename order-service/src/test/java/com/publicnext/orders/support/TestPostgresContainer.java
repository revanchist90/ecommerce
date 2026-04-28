package com.publicnext.orders.support;

import org.testcontainers.containers.PostgreSQLContainer;

public final class TestPostgresContainer {

    public static final PostgreSQLContainer<?> INSTANCE;

    static {
        INSTANCE = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("order_test")
                .withUsername("test")
                .withPassword("test");
        INSTANCE.start();
    }

    private TestPostgresContainer() {}
}
