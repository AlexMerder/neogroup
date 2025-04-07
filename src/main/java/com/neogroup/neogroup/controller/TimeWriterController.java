package com.neogroup.neogroup.controller;


import com.neogroup.neogroup.dto.TimeRecordsResponse;
import com.neogroup.neogroup.service.TimeRecords;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/time/records")
public class TimeWriterController {

    private final TimeRecords timeRecords;

    public TimeWriterController(TimeRecords timeRecords) {
        this.timeRecords = timeRecords;
    }

    @GetMapping
    public ResponseEntity<TimeRecordsResponse> getTimeRecords() {
        return ResponseEntity.ok(timeRecords.getTimeRecords());
    }
}
