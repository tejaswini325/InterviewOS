package com.interviewos.controller;

import com.interviewos.domain.*;
import com.interviewos.dto.InterviewDtos.*;
import com.interviewos.repository.BookmarkRepository;
import com.interviewos.repository.QuestionRepository;
import java.time.Clock;
import com.interviewos.dto.ReportDtos.*;
import com.interviewos.exception.ApiException;
import com.interviewos.jobs.JobService;
import com.interviewos.repository.JobRepository;
import com.interviewos.repository.SessionRepository;
import com.interviewos.security.AuthUser;
import com.interviewos.service.AnalyticsService;
import com.interviewos.service.QuestionCatalog;
import com.interviewos.service.QuestionCatalog.TopicInfo;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MiscControllers {
    private final AnalyticsService analytics;
    private final QuestionCatalog catalog;
    private final JobRepository jobs;
    private final SessionRepository sessions;
    private final JobService jobService;
    private final BookmarkRepository bookmarks;
    private final QuestionRepository questions;
    private final Clock clock;

    public MiscControllers(AnalyticsService analytics, QuestionCatalog catalog, JobRepository jobs, SessionRepository sessions, JobService jobService,
                         BookmarkRepository bookmarks, QuestionRepository questions, Clock clock) {
        this.bookmarks = bookmarks; this.questions = questions; this.clock = clock;
        this.analytics = analytics; this.catalog = catalog; this.jobs = jobs; this.sessions = sessions; this.jobService = jobService;
    }

    @GetMapping("/analytics/overview")
    public Overview overview(@AuthenticationPrincipal AuthUser u) { return analytics.overview(u.id()); }

    @GetMapping("/analytics/skills")
    public List<SkillView> skills(@AuthenticationPrincipal AuthUser u) { return analytics.skills(u.id()); }

    @GetMapping("/questions/topics")
    public Map<String, Object> topics() { return Map.of("topics", catalog.topics(), "codingEnabled", catalog.codingEnabled()); }

    /** Failed-task inspection. Candidates see their own jobs without internal error text; admins see everything. */
    @GetMapping("/jobs")
    public List<JobView> jobs(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) JobStatus status) {
        boolean admin = u.role() == Role.ADMIN;
        List<EvaluationJob> list = admin
                ? (status == null ? jobs.findAll() : jobs.findByStatusOrderByUpdatedAtDesc(status))
                : (status == null ? jobs.findAllForUser(u.id()) : jobs.findForUserByStatus(u.id(), status));
        return list.stream().map(j -> new JobView(j.getId(), j.getKind().name(), j.getSessionId(), j.getStatus().name(), j.getAttempts(),
                admin ? j.getLastError() : null, j.getUpdatedAt())).toList();
    }

    @PostMapping("/jobs/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retry(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        EvaluationJob j = jobs.findById(id).orElseThrow(() -> ApiException.notFound("Job"));
        boolean owns = sessions.findByIdAndUserId(j.getSessionId(), u.id()).isPresent();
        if (!owns && u.role() != Role.ADMIN) throw ApiException.notFound("Job");
        jobService.retry(id);
    }

    // ---- practice: suggestions and bookmarks
    @GetMapping("/practice/suggestions")
    public List<PracticeSuggestion> suggestions(@AuthenticationPrincipal AuthUser u) { return analytics.suggestions(u.id()); }

    @GetMapping("/bookmarks")
    public List<BookmarkView> bookmarks(@AuthenticationPrincipal AuthUser u) {
        return bookmarks.findByUserIdOrderByCreatedAtDesc(u.id()).stream().flatMap(b -> questions.findById(b.getQuestionId()).stream()
                .map(q -> new BookmarkView(q.getId(), q.getTopic(), q.getSubtopic(), q.getDifficulty(), q.getType(), q.getStatement(), b.getCreatedAt()))).toList();
    }

    @PostMapping("/bookmarks/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addBookmark(@AuthenticationPrincipal AuthUser u, @PathVariable Long questionId) {
        if (!questions.existsById(questionId)) throw ApiException.notFound("Question");
        if (bookmarks.findByUserIdAndQuestionId(u.id(), questionId).isPresent()) return;   // idempotent
        Bookmark b = new Bookmark();
        b.setUserId(u.id()); b.setQuestionId(questionId); b.setCreatedAt(clock.instant());
        bookmarks.save(b);
    }

    @DeleteMapping("/bookmarks/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(@AuthenticationPrincipal AuthUser u, @PathVariable Long questionId) {
        bookmarks.findByUserIdAndQuestionId(u.id(), questionId).ifPresent(bookmarks::delete);
    }
}
