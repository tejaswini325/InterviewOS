package com.interviewos.jobs;

import com.interviewos.config.AppProperties;
import com.interviewos.domain.JobStatus;
import com.interviewos.domain.SessionStatus;
import com.interviewos.repository.JobRepository;
import com.interviewos.repository.SessionRepository;
import com.interviewos.service.InterviewService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Safety net: redelivers pending/failed jobs (retry delay), recovers crashed consumers, completes abandoned interviews. */
@Component
public class JobSweeper {
    private static final Logger log = LoggerFactory.getLogger(JobSweeper.class);
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(2);
    private final JobRepository jobs;
    private final SessionRepository sessions;
    private final JobService jobService;
    private final JobDispatcher dispatcher;
    private final InterviewService interviews;
    private final AppProperties props;
    private final Clock clock;

    public JobSweeper(JobRepository jobs, SessionRepository sessions, JobService jobService, JobDispatcher dispatcher,
                      InterviewService interviews, AppProperties props, Clock clock) {
        this.jobs = jobs; this.sessions = sessions; this.jobService = jobService; this.dispatcher = dispatcher;
        this.interviews = interviews; this.props = props; this.clock = clock;
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 10_000)
    public void scheduled() {
        try { sweep(Duration.ofSeconds(5)); } catch (RuntimeException e) { log.warn("Sweeper run failed: {}", e.getMessage()); }
    }

    public void sweep(Duration retryDelay) {
        Instant now = clock.instant();
        int reset = jobService.resetStale(now.minusSeconds(props.evaluation().staleProcessingSeconds()));
        if (reset > 0) log.warn("Recovered {} stale job(s)", reset);
        jobs.findByStatusAndUpdatedAtBefore(JobStatus.PENDING, now.minus(retryDelay)).forEach(j -> dispatcher.dispatch(j.getId()));
        sessions.findByStatusAndDeadlineAtBefore(SessionStatus.IN_PROGRESS, now.minus(ABANDONED_AFTER))
                .forEach(s -> interviews.completeExpired(s.getId()));
    }
}
