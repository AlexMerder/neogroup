package com.neogroup.neogroup.repository;

import com.neogroup.neogroup.entity.TimeRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeRecordsRepository extends MongoRepository<TimeRecord, Long> {
    List<TimeRecord> findAllByOrderByTimeAsc();
}
