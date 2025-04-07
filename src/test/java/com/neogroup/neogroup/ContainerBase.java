package com.neogroup.neogroup;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;

@Testcontainers
public class ContainerBase {


    @Container
    public static final GenericContainer<?> mongoDB = new FixedHostPortGenericContainer<>("mongo:4.4.6")
        .withExposedPorts(27017)
        .withFixedExposedPort(27017, 27017)
        .withEnv("MONGO_INITDB_ROOT_USERNAME", "test")
        .withEnv("MONGO_INITDB_ROOT_PASSWORD", "test")
        .withEnv("MONGO_INITDB_DATABASE", "test")
        .waitingFor(Wait.forListeningPort())
        .withReuse(true)
        .withFileSystemBind("./mongo-data", "/data/db", BindMode.READ_WRITE)
        .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    public static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0")
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.topic", () -> "time-records-topic-test");
        registry.add("kafka.topic.groupId", () -> "time-processing-group-test");

        registry.add("spring.data.mongodb.uri", () ->
            String.format("mongodb://%s:%s@%s:%d/%s?authSource=admin",
                "test", "test", mongoDB.getHost(), mongoDB.getFirstMappedPort(), "test"));
    }

    @BeforeAll
    static void setUp() {
        mongoDB.start();
        kafka.start();
    }
}