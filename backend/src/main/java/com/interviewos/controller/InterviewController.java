package com.interviewos.controller;

import com.interviewos.domain.*;
import com.interviewos.dto.InterviewDtos.*;
import com.interviewos.dto.ReportDtos.ReportView;
import com.interviewos.security.AuthUser;
import com.interviewos.service.InterviewService;
import com.interviewos.service.FollowUpService;
import com.interviewos.service.ReportService;
import com.interviewos.service.TranscriptService;
import java.util.List;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService interviews;
    private final ReportService reports;
    private final FollowUpService followUps;
    private final TranscriptService transcript;

    public InterviewController(InterviewService interviews, ReportService reports, FollowUpService followUps, TranscriptService transcript) {
        this.interviews = interviews; this.reports = reports; this.followUps = followUps; this.transcript = transcript;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView start(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody StartRequest r) { return interviews.start(u.id(), r); }

    @GetMapping
    public PageResponse<SessionSummary> list(@AuthenticationPrincipal AuthUser u,
                                             @RequestParam(required = false) SessionStatus status,
                                             @RequestParam(required = false) TargetRole role,
                                             @RequestParam(required = false) InterviewMode mode,
                                             @RequestParam(required = false) Topic topic,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return interviews.list(u.id(), status, role, mode, topic, from, to, page, size);
    }

    @GetMapping("/{id}")
    public SessionView get(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return interviews.get(u.id(), id); }

    @PostMapping("/{id}/next-question")
    public NextQuestionResponse next(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return interviews.nextQuestion(u.id(), id); }

    @PostMapping("/{id}/answers")
    public AnswerAck answer(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody AnswerRequest r) {
        AnswerAck ack = interviews.submitAnswer(u.id(), id, r);
        // follow-up generation runs after the answer transaction has committed, so a slow AI call never blocks or rolls back the answer
        TurnView followUp = followUps.generate(u.id(), id, r.interviewQuestionId()).orElse(null);
        return new AnswerAck(ack.answerId(), ack.duplicate(), ack.answeredCount(), ack.questionCount(), followUp);
    }

    @PostMapping("/{id}/follow-up")
    public TurnView followUpReply(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody FollowUpAnswerRequest r) {
        return interviews.submitFollowUp(u.id(), id, r);
    }

    @GetMapping("/{id}/transcript")
    public List<TranscriptMessage> transcript(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return transcript.forUser(u.id(), id); }

    @PostMapping("/{id}/complete")
    public SessionView complete(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) { return interviews.complete(u.id(), id); }

    /** 200 when ready, 202 while the report job is still running, 200 with status FAILED if generation failed permanently. */
    @GetMapping("/{id}/report")
    public ResponseEntity<ReportView> report(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        ReportView v = reports.view(u.id(), id);
        return ResponseEntity.status("GENERATING".equals(v.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK).body(v);
    }
}
