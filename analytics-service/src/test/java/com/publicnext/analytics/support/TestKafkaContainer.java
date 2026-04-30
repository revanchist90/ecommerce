package com.publicnext.analytics.support;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestKafkaContainer {

    public static final KafkaContainer INSTANCE;

    static {
        INSTANCE = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
        INSTANCE.start();
    }

    private TestKafkaContainer() {
    }
}
