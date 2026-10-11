package com.interviewos.jobs;

import com.interviewos.ai.AiProvider;
import com.interviewos.domain.EvaluationJob;
import com.interviewos.domain.JobKind;
import com.interviewos.dto.ReportDtos.ReportData;
import com.interviewos.service.EvaluationService;
import com.interviewos.service.ReportService;
import org.springframework.stereotype.Component;

@Component
public class ReportJobHandler implements JobHandler {
    private final EvaluationService evaluation;
    private final ReportService reports;
    private final AiProvider ai;

    public ReportJobHandler(EvaluationService evaluation, ReportService reports, AiProvider ai) {
        this.evaluation = evaluation; this.reports = reports; this.ai = ai;
    }

    @Override public JobKind kind() { return JobKind.REPORT; }

    @Override
    public void handle(EvaluationJob job) {
        Long sessionId = job.getRefId();
        if (reports.exists(sessionId)) return;                                           // idempotent
        evaluation.pendingAnswerIds(sessionId).forEach(evaluation::refine);              // never report on half-evaluated answers
        ReportData data = reports.build(sessionId);
        String summary = ai.enabled() ? ai.summarize(ReportService.facts(data)).orElse(null) : null;   // external call outside any DB transaction
        reports.persist(sessionId, data, summary);
    }
}
