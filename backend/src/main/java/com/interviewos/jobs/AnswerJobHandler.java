package com.interviewos.jobs;

import com.interviewos.domain.EvaluationJob;
import com.interviewos.domain.JobKind;
import com.interviewos.service.EvaluationService;
import org.springframework.stereotype.Component;

@Component
public class AnswerJobHandler implements JobHandler {
    private final EvaluationService evaluation;

    public AnswerJobHandler(EvaluationService evaluation) { this.evaluation = evaluation; }

    @Override public JobKind kind() { return JobKind.ANSWER; }
    @Override public void handle(EvaluationJob job) { evaluation.refine(job.getRefId()); }
}
