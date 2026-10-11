package com.interviewos.service;

import com.interviewos.domain.*;
import com.interviewos.dto.InterviewDtos.PracticeSuggestion;
import com.interviewos.dto.ReportDtos.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import com.interviewos.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final SessionRepository sessions;
    private final ReportRepository reports;
    private final SkillScoreRepository skills;
    private final ProfileRepository profiles;
    private final BookmarkRepository bookmarks;
    private final QuestionRepository questions;
    private final Clock clock;

    public AnalyticsService(SessionRepository sessions, ReportRepository reports, SkillScoreRepository skills,
                            ProfileRepository profiles, BookmarkRepository bookmarks, QuestionRepository questions, Clock clock) {
        this.sessions = sessions; this.reports = reports; this.skills = skills; this.profiles = profiles;
        this.bookmarks = bookmarks; this.questions = questions; this.clock = clock;
    }

    public Overview overview(Long userId) {
        List<InterviewSession> completed = sessions.findByUserIdAndStatusOrderByStartedAtAsc(userId, SessionStatus.COMPLETED);
        Map<Long, InterviewReport> byId = reports.findBySessionIdIn(completed.stream().map(InterviewSession::getId).toList())
                .stream().collect(Collectors.toMap(InterviewReport::getSessionId, r -> r));
        List<TrendPoint> trend = completed.stream().filter(s -> byId.containsKey(s.getId()))
                .map(s -> new TrendPoint(s.getId(), s.getStartedAt(), s.getTargetRole().name(), byId.get(s.getId()).getOverallScore())).toList();
        List<TrendPoint> last = trend.size() > 30 ? trend.subList(trend.size() - 30, trend.size()) : trend;
        Double avg = trend.isEmpty() ? null : Math.round(trend.stream().mapToDouble(TrendPoint::overallScore).average().orElse(0) * 100) / 100.0;
        Double best = trend.stream().map(TrendPoint::overallScore).max(Double::compare).orElse(null);
        Double latest = trend.isEmpty() ? null : trend.get(trend.size() - 1).overallScore();
        Instant weekStart = LocalDate.now(clock.withZone(ZoneOffset.UTC)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC).toInstant();
        long thisWeek = completed.stream().filter(c -> !c.getStartedAt().isBefore(weekStart)).count();
        int goal = profiles.findByUserId(userId).map(CandidateProfile::getWeeklyGoal).orElse(3);
        return new Overview(sessions.countByUserId(userId), completed.size(), sessions.countByUserIdAndStatus(userId, SessionStatus.IN_PROGRESS),
                sessions.countByUserIdAndStatus(userId, SessionStatus.FAILED), avg, best, latest, last, thisWeek, goal);
    }

    /** Long-term score per topic: an exponential moving average over completed interviews (0-100). */
    public List<SkillView> skills(Long userId) {
        return skills.findByUserIdOrderByTopic(userId).stream()
                .map(s -> new SkillView(s.getTopic().name(), Math.round(s.getScore() * 10000) / 100.0, s.getAttempts(), s.getUpdatedAt())).toList();
    }

    /** Next-practice suggestions. Each says what it is based on, so nothing is presented as more than it is. */
    public List<PracticeSuggestion> suggestions(Long userId) {
        List<PracticeSuggestion> out = new ArrayList<>();
        List<SkillScore> all = skills.findByUserIdOrderByTopic(userId);

        all.stream().filter(sk -> sk.getScore() < 0.6).sorted(Comparator.comparingDouble(SkillScore::getScore)).limit(2).forEach(sk ->
            out.add(new PracticeSuggestion("Strengthen " + sk.getTopic(), "Your measured " + sk.getTopic() + " score is " + Math.round(sk.getScore() * 100) + "% over " + sk.getAttempts() + " question(s).",
                    "MEASURED", InterviewMode.forTopic(sk.getTopic()), List.of(sk.getTopic()), sk.getScore() < 0.4 ? Difficulty.EASY : Difficulty.MEDIUM)));

        Map<Topic, Long> marked = new EnumMap<>(Topic.class);
        for (Bookmark b : bookmarks.findByUserIdOrderByCreatedAtDesc(userId)) {
            questions.findById(b.getQuestionId()).ifPresent(q -> marked.merge(q.getTopic(), 1L, Long::sum));
        }
        marked.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(en ->
            out.add(new PracticeSuggestion("Revisit your bookmarked " + en.getKey() + " questions", en.getValue() + " bookmarked question(s) in this topic.",
                    "BOOKMARKS", InterviewMode.forTopic(en.getKey()), List.of(en.getKey()), Difficulty.MEDIUM)));

        Set<Topic> seen = all.stream().map(SkillScore::getTopic).collect(Collectors.toSet());
        for (Topic t : Topic.values()) {
            if (out.size() >= 4) break;
            if (!seen.contains(t)) out.add(new PracticeSuggestion("Try " + t, "You have not practised " + t + " yet.", "NEW_TOPIC",
                    InterviewMode.forTopic(t), List.of(t), Difficulty.MEDIUM));
        }
        return out.size() > 4 ? out.subList(0, 4) : out;
    }
}
