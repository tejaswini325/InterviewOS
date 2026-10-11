package com.interviewos.jobs;

import com.interviewos.config.AppProperties;
import com.interviewos.domain.*;
import com.interviewos.repository.JobRepository;
import com.interviewos.repository.SessionRepository;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes one job with: atomic claim (idempotency across duplicate deliveries / multiple consumers),
 * persistent status, bounded retries and dead-lettering.
 * Every DB step uses REQUIRES_NEW because in inline mode this runs from an afterCommit callback.
 */
@Component
public class JobProcessor {
    private static final Logger log = LoggerFactory.getLogger(JobProcessor.class);
    private final JobRepository jobs;
    private final SessionRepository sessions;
    private final JobDispatcher dispatcher;
    private final AppProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Map<JobKind, JobHandler> handlers = new EnumMap<>(JobKind.class);

    public JobProcessor(JobRepository jobs, SessionRepository sessions, JobDispatcher dispatcher, AppProperties props,
                        Clock clock, PlatformTransactionManager tm, List<JobHandler> handlerList) {
        this.jobs = jobs; this.sessions = sessions; this.dispatcher = dispatcher; this.props = props; this.clock = clock;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        handlerList.forEach(h -> handlers.put(h.kind(), h));
    }

    public void process(long jobId) {
        Integer claimed = tx.execute(s -> jobs.claim(jobId, JobStatus.PENDING, JobStatus.PROCESSING, clock.instant()));
        if (claimed == null || claimed == 0) {
            log.debug("Job {} not claimable (already processed or in progress): skipping", jobId);
            return;
        }
        EvaluationJob job = tx.execute(s -> jobs.findById(jobId).orElseThrow());
        try {
            handlers.get(job.getKind()).handle(job);
            tx.executeWithoutResult(s -> {
                EvaluationJob j = jobs.findById(jobId).orElseThrow();
                j.setStatus(JobStatus.DONE); j.setLastError(null); j.setUpdatedAt(clock.instant());
            });
            log.info("Job {} ({}) done after {} attempt(s)", jobId, job.getKind(), job.getAttempts());
        } catch (Exception e) {
            fail(jobId, e);
        }
    }

    private void fail(long jobId, Exception e) {
        String msg = (e.getClass().getSimpleName() + ": " + e.getMessage());
        String trimmed = msg.length() > 1000 ? msg.substring(0, 1000) : msg;
        boolean dead = Boolean.TRUE.equals(tx.execute(s -> {
            EvaluationJob j = jobs.findById(jobId).orElseThrow();
            j.setLastError(trimmed);
            j.setUpdatedAt(clock.instant());
            if (j.getAttempts() >= props.evaluation().maxAttempts()) {
                j.setStatus(JobStatus.DEAD);
                if (j.getKind() == JobKind.REPORT) {
                    sessions.lockById(j.getSessionId()).ifPresent(sess -> sess.setStatus(SessionStatus.FAILED));
                }
                return true;
            }
            j.setStatus(JobStatus.PENDING);   // the sweeper redelivers after a short delay
            return false;
        }));
        if (dead) {
            log.error("Job {} failed permanently: {}", jobId, trimmed);
            dispatcher.deadLetter(jobId, trimmed);
        } else {
            log.warn("Job {} failed, will retry: {}", jobId, trimmed);
        }
    }
}
