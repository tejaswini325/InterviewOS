package com.interviewos.repository;

import com.interviewos.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EvaluationRepository extends JpaRepository<AnswerEvaluation, Long> {
    Optional<AnswerEvaluation> findByAnswerId(Long answerId);

    interface ScoreRow { Long getQuestionId(); Double getScore(); }

    @Query("select iq.questionId as questionId, e.score as score from InterviewQuestion iq, CandidateAnswer a, AnswerEvaluation e " +
           "where iq.sessionId = :sid and a.interviewQuestionId = iq.id and e.answerId = a.id")
    List<ScoreRow> scoresBySession(@Param("sid") Long sessionId);

    @Query("select e from AnswerEvaluation e where e.finalized = false and e.answerId in " +
           "(select a.id from CandidateAnswer a, InterviewQuestion iq where a.interviewQuestionId = iq.id and iq.sessionId = :sid)")
    List<AnswerEvaluation> unfinalizedBySession(@Param("sid") Long sessionId);
}
