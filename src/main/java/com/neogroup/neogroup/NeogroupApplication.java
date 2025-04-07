package com.neogroup.neogroup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.neogroup.neogroup.repository")
public class NeogroupApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeogroupApplication.class, args);
    }

}
