package com.publicnext.analytics.support;

import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestMongoContainer {

    public static final MongoDBContainer INSTANCE;

    static {
        INSTANCE = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
        INSTANCE.start();
    }

    private TestMongoContainer() {
    }
}
