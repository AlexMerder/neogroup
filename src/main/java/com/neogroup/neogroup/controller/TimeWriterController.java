package com.neogroup.neogroup.controller;


import com.neogroup.neogroup.dto.TimeRecordsResponse;
import com.neogroup.neogroup.service.TimeRecords;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/time/records")
@Slf4j
public class TimeWriterController {

    private final TimeRecords timeRecords;

    public TimeWriterController(TimeRecords timeRecords) {
        this.timeRecords = timeRecords;
    }

    @GetMapping
    public ResponseEntity<TimeRecordsResponse> getTimeRecords() {
        try {
            TimeRecordsResponse recordsResponse = timeRecords.getTimeRecords();
            if (recordsResponse == null) {
                log.warn("No records found");
                return ResponseEntity.notFound().build();
            }
            log.info("Returning records");
            return ResponseEntity.ok(recordsResponse);
        } catch (Exception e) {
            log.error("Error getting records: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
