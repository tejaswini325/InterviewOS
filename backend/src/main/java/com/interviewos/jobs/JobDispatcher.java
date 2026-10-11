package com.interviewos.jobs;

/** Transport for job ids. Kafka in production; an inline implementation keeps the workflow testable without a broker. */
public interface JobDispatcher {
    void dispatch(long jobId);
    void deadLetter(long jobId, String reason);
}
