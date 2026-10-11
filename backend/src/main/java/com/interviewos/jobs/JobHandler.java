package com.interviewos.jobs;

import com.interviewos.domain.EvaluationJob;
import com.interviewos.domain.JobKind;

public interface JobHandler {
    JobKind kind();
    /** Must be idempotent: the same job can be delivered more than once. */
    void handle(EvaluationJob job) throws Exception;
}
