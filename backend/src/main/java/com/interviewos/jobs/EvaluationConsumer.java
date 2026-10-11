package com.interviewos.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer side. Failures are handled inside JobProcessor (bounded attempts, then dead-letter),
 * so the listener never throws and the offset always advances: no poison-pill loop.
 */
@Component
@ConditionalOnProperty(name = "interviewos.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class EvaluationConsumer {
    private static final Logger log = LoggerFactory.getLogger(EvaluationConsumer.class);
    private final JobProcessor processor;

    public EvaluationConsumer(JobProcessor processor) { this.processor = processor; }

    @KafkaListener(topics = "${interviewos.kafka.topic}", concurrency = "3")
    public void onMessage(String payload) {
        try {
            processor.process(Long.parseLong(payload.trim()));
        } catch (NumberFormatException e) {
            log.error("Discarding malformed job message");
        }
    }
}
