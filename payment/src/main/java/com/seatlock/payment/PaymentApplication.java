package com.seatlock.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentApplication {
    static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
