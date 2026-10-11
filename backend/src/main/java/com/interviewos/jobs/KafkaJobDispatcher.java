package com.interviewos.jobs;

import com.interviewos.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Producer side. The message carries only the job id; PostgreSQL stays the source of truth. */
@Component
@ConditionalOnProperty(name = "interviewos.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaJobDispatcher implements JobDispatcher {
    private static final Logger log = LoggerFactory.getLogger(KafkaJobDispatcher.class);
    private final KafkaTemplate<String, String> kafka;
    private final AppProperties props;

    public KafkaJobDispatcher(KafkaTemplate<String, String> kafka, AppProperties props) { this.kafka = kafka; this.props = props; }

    @Override public void dispatch(long jobId) {
        String id = String.valueOf(jobId);
        kafka.send(props.kafka().topic(), id, id).whenComplete((r, ex) -> {
            if (ex != null) log.warn("Publish failed for job {} (the sweeper will redeliver): {}", jobId, ex.getMessage());
        });
    }

    @Override public void deadLetter(long jobId, String reason) {
        kafka.send(props.kafka().dlqTopic(), String.valueOf(jobId), jobId + "|" + reason);
        log.error("Job {} moved to dead-letter topic {}: {}", jobId, props.kafka().dlqTopic(), reason);
    }
}
