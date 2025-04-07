package com.neogroup.neogroup;

import com.neogroup.neogroup.config.KafkaTestConfig;
import com.neogroup.neogroup.entity.TimeRecord;
import com.neogroup.neogroup.repository.TimeRecordsRepository;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "scheduling.enabled=false")
@Testcontainers
@Import(KafkaTestConfig.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TimeRecordIntegrationTest extends ContainerBase {

    @Autowired
    private TimeRecordsRepository timeRecordsRepository;

    @Autowired
    private KafkaTemplate<String, TimeRecord> kafkaTemplate;

    @AfterEach
    void tearDown() {
        timeRecordsRepository.deleteAll();
    }
    @BeforeEach
    void cleanUp() {
        timeRecordsRepository.deleteAll();
    }

    @Test
    @DisplayName("Test that record is saved to MongoDB")
    @Order(1)
    void testSaveRecordToMongoDb() {
        TimeRecord timeRecord = new TimeRecord();
        timeRecord.setTime(getCurrentTime());

        timeRecordsRepository.save(timeRecord);

        List<TimeRecord> records = timeRecordsRepository.findAll();
        assertEquals(1, records.size(), "One timeRecord should be saved");
        assertEquals(timeRecord.getTime(), records.getFirst().getTime(),
            "Record time should match");
    }

    @Test
    @DisplayName("Test that records are saved to MongoDB when the database is available")
    @Order(2)
    void testConsumeAndSaveRecordsWhenDbIsAvailable() {
        TimeRecord record1 = new TimeRecord();
        record1.setTime(getCurrentTime());

        kafkaTemplate.send("time-records-topic-test", record1);

        TimeRecord record2 = new TimeRecord();
        record2.setTime(getCurrentTime());

        kafkaTemplate.send("time-records-topic-test", record2);

        await()
            .atMost(10, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .until(() -> !timeRecordsRepository.findAll().isEmpty());

        List<TimeRecord> records = timeRecordsRepository.findAll();
        assertEquals(2, records.size(), "Two records should be saved");
    }

    @Test
    @DisplayName("Test database connection loss and recovery with Kafka message buffering")
    @Order(3)
    void testDatabaseConnectionLossAndRecovery() {
        TimeRecord record1 = new TimeRecord();
        record1.setTime(getCurrentTime());
        kafkaTemplate.send("time-records-topic-test", record1);

        await()
            .atMost(60, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .until(() -> !timeRecordsRepository.findAll().isEmpty());

        List<TimeRecord> initialRecords = timeRecordsRepository.findAll();
        assertEquals(1, initialRecords.size(), "One record should be saved initially");
        assertEquals(record1.getTime(), initialRecords.getFirst().getTime(), "Record time should match");

        System.out.println("Stopping MongoDB container");
        mongoDB.stop();

        await().atMost(Duration.ofSeconds(10))
            .until(() -> !mongoDB.isHealthy());

        TimeRecord record2 = new TimeRecord();
        record2.setTime(getCurrentTime());
        kafkaTemplate.send("time-records-topic-test", record2);
        System.out.println("The message has been sent to Kafka topic:\n" + record2);

        System.out.println("Starting MongoDB container");
        mongoDB.start();

        System.out.println("Waiting for MongoDB connection...");
        await()
            .atMost(60, SECONDS)
            .pollInterval(1, SECONDS)
            .until(() -> {
                try {
                    timeRecordsRepository.count();
                    System.out.println("MongoDB connection established.");
                    return true;
                } catch (Exception e) {
                    System.out.println("Still waiting for MongoDB: " + e.getMessage());
                    return false;
                }
            });


        await()
            .atMost(60, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .until(() -> !timeRecordsRepository.findAll().isEmpty());

        List<TimeRecord> finalRecords = timeRecordsRepository.findAll();
        assertEquals(2, finalRecords.size(), "All records should be saved after recovery");
        assertEquals(record1.getTime(), finalRecords.get(0).getTime(), "First record time should match");
        assertEquals(record2.getTime(), finalRecords.get(1).getTime(), "Second record time should match");
    }

    private static String getCurrentTime() {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    @PreDestroy
    public void shutDownSchedulers() {
        timeRecordsRepository.deleteAll();
    }
}