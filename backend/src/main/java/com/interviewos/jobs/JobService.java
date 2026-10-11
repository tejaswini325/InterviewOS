package com.interviewos.jobs;

import com.interviewos.domain.*;
import com.interviewos.exception.ApiException;
import com.interviewos.repository.JobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final JobRepository jobs;
    private final JobDispatcher dispatcher;
    private final Clock clock;

    public JobService(JobRepository jobs, JobDispatcher dispatcher, Clock clock) { this.jobs = jobs; this.dispatcher = dispatcher; this.clock = clock; }

    /** Idempotent by key. The message is published only after the transaction that created the job commits. */
    @Transactional
    public EvaluationJob enqueue(JobKind kind, Long refId, Long sessionId, String key) {
        return jobs.findByIdempotencyKey(key).orElseGet(() -> {
            EvaluationJob j = new EvaluationJob();
            j.setKind(kind); j.setRefId(refId); j.setSessionId(sessionId); j.setIdempotencyKey(key);
            j.setStatus(JobStatus.PENDING); j.setAttempts(0);
            j.setCreatedAt(clock.instant()); j.setUpdatedAt(clock.instant());
            jobs.save(j);
            publish(j.getId());
            return j;
        });
    }

    @Transactional
    public void retry(Long jobId) {
        EvaluationJob j = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job"));
        if (j.getStatus() != JobStatus.DEAD) throw ApiException.conflict("NOT_RETRYABLE", "Only dead jobs can be retried");
        j.setStatus(JobStatus.PENDING); j.setAttempts(0); j.setLastError(null); j.setUpdatedAt(clock.instant());
        publish(j.getId());
    }

    /** Jobs whose consumer crashed mid-flight go back to PENDING. */
    @Transactional
    public int resetStale(Instant cutoff) {
        List<EvaluationJob> stale = jobs.findByStatusAndUpdatedAtBefore(JobStatus.PROCESSING, cutoff);
        stale.forEach(j -> { j.setStatus(JobStatus.PENDING); j.setUpdatedAt(clock.instant()); });
        return stale.size();
    }

    public void publish(Long jobId) {
        AfterCommit.run(() -> {
            try { dispatcher.dispatch(jobId); }
            catch (RuntimeException e) { log.warn("Dispatch of job {} failed; the sweeper will retry: {}", jobId, e.getMessage()); }
        });
    }
}
