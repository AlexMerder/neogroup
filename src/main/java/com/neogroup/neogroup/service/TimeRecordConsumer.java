package com.neogroup.neogroup.service;

import com.neogroup.neogroup.entity.TimeRecord;
import com.neogroup.neogroup.repository.TimeRecordsRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class TimeRecordConsumer {

    private final TimeRecordsRepository timeRecordsRepository;
    private final ExecutorService dbWriteExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();

    private final Queue<TimeRecord> bufferQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isDbAvailable = new AtomicBoolean(true);
    private ScheduledFuture<?> retryFuture;

    @Value("${app.db.retry.interval}")
    private long retryInterval;

    @Autowired
    public TimeRecordConsumer(TimeRecordsRepository timeRecordsRepository) {
        this.timeRecordsRepository = timeRecordsRepository;
    }

    @KafkaListener(topics = "${kafka.topic}", groupId = "${kafka.groupId}")
    public void consume(TimeRecord timeRecord) {
        bufferQueue.offer(timeRecord);
        processQueue();
    }

    private synchronized void processQueue() {
        log.info("Attempting to process buffer queue. Queue size: {}", bufferQueue.size());
        dbWriteExecutor.submit(() -> {
            while (!bufferQueue.isEmpty()) {
                TimeRecord timeRecord = bufferQueue.peek();
                try {
                    timeRecordsRepository.save(timeRecord);
                    log.info("Saved timeRecord to database: {}", timeRecord.getTime());
                    bufferQueue.poll();
                    isDbAvailable.set(true);
                } catch (DataAccessResourceFailureException ex) {
                    if (isDbAvailable.compareAndSet(true, false)) {
                        log.error("The connection to DB is lost.");
                    }
                    schedulePeriodicRetry();
                    break;
                } catch (Exception fatalException) {
                    log.error("Unexpected failure, saving the timeRecord to buffer: {}",
                        fatalException.getMessage());
                    schedulePeriodicRetry();
                }
            }
        });
    }

    private synchronized void schedulePeriodicRetry() {
        if (retryFuture != null && !retryFuture.isDone()) {
            retryFuture.cancel(false);
        }

        retryFuture = retryScheduler.scheduleAtFixedRate(() -> {
            if (checkDatabaseConnection()) {
                retryBufferedRecords();
            }
        }, 0, retryInterval, TimeUnit.MILLISECONDS);
    }

    private boolean checkDatabaseConnection() {
        try {
            timeRecordsRepository.count();
            if (!isDbAvailable.getAndSet(true)) {
                log.info("MongoDB is back online!");
            }
            return true;
        } catch (DataAccessResourceFailureException e) {
            log.warn("MongoDB still offline: {}", e.getMessage());
            isDbAvailable.set(false);
            return false;
        }
    }

    private synchronized void retryBufferedRecords() {
        log.info("Attempting to retry buffered records - Queue size: {}", bufferQueue.size());
        while (isDbAvailable.get() && !bufferQueue.isEmpty()) {
            processQueue();
        }
        if (bufferQueue.isEmpty()) {
            stopPeriodicRetry();
        }
    }


    private synchronized void stopPeriodicRetry() {
        if (retryFuture != null && !retryFuture.isCancelled()) {
            retryFuture.cancel(true);
            retryFuture = null;
        }
        isDbAvailable.set(true);
        log.info("Connection has been re-established.");
    }

    @PreDestroy
    public void shutDownSchedulers() {
        log.info("Initiating graceful shutdown...");

        stopPeriodicRetry();

        processQueue();

        dbWriteExecutor.shutdown();
        retryScheduler.shutdown();

        try {
            if (!dbWriteExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of dbWriteExecutor");
                dbWriteExecutor.shutdownNow();
            }

            if (!retryScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of retryScheduler");
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Shutdown process interrupted: {}", e.getMessage());
            dbWriteExecutor.shutdownNow();
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (!bufferQueue.isEmpty()) {
            log.warn(
                "Some buffered TimeRecords were not persisted. Attempting to save synchronously before exiting.");
            try {
                while (!bufferQueue.isEmpty()) {
                    timeRecordsRepository.save(bufferQueue.poll());
                }
                log.info("All buffered TimeRecords successfully saved on shutdown.");
            } catch (Exception ex) {
                log.error("Could not persist buffered TimeRecords on shutdown: {}",
                    ex.getMessage());
            }
        }
        log.info("Application shutdown completed gracefully.");
    }
}