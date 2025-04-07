package com.neogroup.neogroup.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "time_records")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TimeRecord {

    @Id
    private String id;

    @Indexed
    private String time;

    public TimeRecord(String time) {
        this.time = time;
    }
}