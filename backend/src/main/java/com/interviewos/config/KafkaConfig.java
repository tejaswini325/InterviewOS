package com.interviewos.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "interviewos.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {
    @Bean NewTopic jobsTopic(AppProperties p) { return TopicBuilder.name(p.kafka().topic()).partitions(3).replicas(1).build(); }
    @Bean NewTopic dlqTopic(AppProperties p) { return TopicBuilder.name(p.kafka().dlqTopic()).partitions(1).replicas(1).build(); }
}
