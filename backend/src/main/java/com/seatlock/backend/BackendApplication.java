package com.seatlock.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BackendApplication {
    static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
