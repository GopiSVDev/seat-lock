package com.seatlock.backend.booking.internal.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class KafkaTopicsConfig {

    // Created at startup if missing. Key = booking id, so one booking's events stay in order on one partition.
    // One replica: a single local broker.
    @Bean
    NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(BookingConfirmed.TOPIC).partitions(3).replicas(1).build();
    }
}
