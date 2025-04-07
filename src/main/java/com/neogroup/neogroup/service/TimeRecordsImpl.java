package com.neogroup.neogroup.service;

import com.neogroup.neogroup.dto.TimeRecordDto;
import com.neogroup.neogroup.dto.TimeRecordsResponse;
import com.neogroup.neogroup.entity.TimeRecord;
import com.neogroup.neogroup.repository.TimeRecordsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class TimeRecordsImpl implements TimeRecords {

    private final TimeRecordsRepository timeRecordsRepository;
    private final KafkaTemplate<String, TimeRecord> kafkaTemplate;

    public TimeRecordsImpl(TimeRecordsRepository timeRecordsRepository,
        KafkaTemplate<String, TimeRecord> kafkaTemplate) {
        this.timeRecordsRepository = timeRecordsRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    private static String getCurrentTime() {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    @Override
    public TimeRecordsResponse getTimeRecords() {

        List<TimeRecord> timeRecords = timeRecordsRepository.findAllByOrderByTimeAsc();

        if (timeRecords.isEmpty()) {
            log.info("No time records found");
            return new TimeRecordsResponse(List.of());
        }
        return new TimeRecordsResponse(timeRecords.stream().map(currentRecord -> {
            TimeRecordDto timeRecordDto = new TimeRecordDto();
            timeRecordDto.setTime(currentRecord.getTime());
            timeRecordDto.setId(currentRecord.getId());
            return timeRecordDto;
        }).toList());
    }

    @Scheduled(fixedRateString = "${scheduling.records.fixed-rate}")
    private void writeTimeEverySecond() {
        TimeRecord timeRecord = new TimeRecord(getCurrentTime());
        kafkaTemplate.send("time-records-topic", timeRecord.getTime(), timeRecord);
        log.info("Published time record to Kafka: {}", timeRecord.getTime());
    }
}