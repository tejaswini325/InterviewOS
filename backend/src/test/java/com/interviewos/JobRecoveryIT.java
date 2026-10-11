package com.interviewos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.interviewos.domain.*;
import com.interviewos.jobs.JobProcessor;
import com.interviewos.jobs.JobService;
import com.interviewos.jobs.JobSweeper;
import com.interviewos.repository.*;
import com.interviewos.service.EvaluationService;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

/** Failure recovery for the asynchronous pipeline, run through the inline dispatcher (no broker needed). */
class JobRecoveryIT extends AbstractIT {
    @Autowired JobRepository jobs;
    @Autowired JobProcessor processor;
    @Autowired JobSweeper sweeper;
    @Autowired JobService jobService;
    @Autowired EvaluationRepository evals;
    @Autowired AnswerRepository answers;
    @SpyBean EvaluationService evaluationService;

    private long submitTextAnswer(String token) throws Exception {
        long id = json(doPost("/api/interviews", token, InterviewLifecycleIT.START)).get("id").asLong();
        for (int i = 0; i < 8; i++) {
            JsonNode q = json(doPost("/api/interviews/" + id + "/next-question", token, null)).get("question");
            boolean text = !"MCQ".equals(q.get("type").asText());
            JsonNode ack = json(doPost("/api/interviews/" + id + "/answers", token, text
                    ? Map.of("interviewQuestionId", q.get("interviewQuestionId").asLong(), "answerText", "It depends on the hashCode and on equals semantics, and the explanation continues here.", "timeSpentSec", 5)
                    : Map.of("interviewQuestionId", q.get("interviewQuestionId").asLong(), "selectedOption", "A", "timeSpentSec", 5)).andExpect(status().isOk()));
            if (text) return ack.get("answerId").asLong();
        }
        throw new AssertionError("no text question was served");
    }

    @Test void failedJobIsRetriedWithBoundedAttemptsThenDeadLetteredThenRecoverable() throws Exception {
        doThrow(new IllegalStateException("boom")).when(evaluationService).refine(anyLong());
        try {
            long answerId = submitTextAnswer(newUser());               // enqueue + inline delivery #1 fails
            EvaluationJob job = jobs.findByIdempotencyKey("answer:" + answerId).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
            assertThat(job.getAttempts()).isEqualTo(1);
            assertThat(job.getLastError()).contains("boom");

            sweeper.sweep(Duration.ZERO);                               // delivery #2 fails
            sweeper.sweep(Duration.ZERO);                               // delivery #3 fails -> max attempts (3)
            job = jobs.findById(job.getId()).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
            assertThat(job.getAttempts()).isEqualTo(3);

            sweeper.sweep(Duration.ZERO);                               // dead jobs are never redelivered automatically
            assertThat(jobs.findById(job.getId()).orElseThrow().getAttempts()).isEqualTo(3);

            Mockito.reset(evaluationService);                           // the fault is fixed: back to the real implementation
            jobService.retry(job.getId());
            job = jobs.findById(job.getId()).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
            assertThat(evals.findByAnswerId(answerId).orElseThrow().isFinalized()).isTrue();
        } finally {
            Mockito.reset(evaluationService);
        }
    }

    @Test void duplicateDeliveryOfACompletedJobIsHarmless() throws Exception {
        long answerId = submitTextAnswer(newUser());
        EvaluationJob job = jobs.findByIdempotencyKey("answer:" + answerId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        int attempts = job.getAttempts();
        double score = evals.findByAnswerId(answerId).orElseThrow().getScore();

        processor.process(job.getId());                                 // duplicate message
        processor.process(job.getId());

        assertThat(jobs.findById(job.getId()).orElseThrow().getAttempts()).isEqualTo(attempts);
        assertThat(evals.findByAnswerId(answerId).orElseThrow().getScore()).isEqualTo(score);
    }

    @Test void enqueueIsIdempotentPerKey() throws Exception {
        long answerId = submitTextAnswer(newUser());
        EvaluationJob j = jobs.findByIdempotencyKey("answer:" + answerId).orElseThrow();
        EvaluationJob again = jobService.enqueue(JobKind.ANSWER, answerId, j.getSessionId(), "answer:" + answerId);
        assertThat(again.getId()).isEqualTo(j.getId());
    }

    @Test void failedJobsAreInspectableByTheirOwnerWithoutInternalErrorText() throws Exception {
        doThrow(new IllegalStateException("secret internal detail")).when(evaluationService).refine(anyLong());
        try {
            String token = newUser();
            submitTextAnswer(token);
            JsonNode list = json(doGet("/api/jobs?status=PENDING", token));
            assertThat(list).isNotEmpty();
            assertThat(list.get(0).has("lastError")).isFalse();
        } finally {
            Mockito.reset(evaluationService);
        }
    }
}
