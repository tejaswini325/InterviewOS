package com.interviewos.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** interviewos.kafka.enabled=false: processes the job in-process right after commit. Used by tests and broker-less demos. */
@Component
@ConditionalOnProperty(name = "interviewos.kafka.enabled", havingValue = "false")
public class InlineJobDispatcher implements JobDispatcher {
    private static final Logger log = LoggerFactory.getLogger(InlineJobDispatcher.class);
    private final JobProcessor processor;

    public InlineJobDispatcher(@Lazy JobProcessor processor) { this.processor = processor; }

    @Override public void dispatch(long jobId) { processor.process(jobId); }
    @Override public void deadLetter(long jobId, String reason) { log.error("Job {} dead-lettered (inline mode): {}", jobId, reason); }
}
