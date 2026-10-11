package com.interviewos.repository;

import com.interviewos.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AnswerRepository extends JpaRepository<CandidateAnswer, Long> {
    Optional<CandidateAnswer> findByInterviewQuestionId(Long interviewQuestionId);

    @Query("select count(a) from CandidateAnswer a where a.interviewQuestionId in (select iq.id from InterviewQuestion iq where iq.sessionId = :sid)")
    long countBySession(@Param("sid") Long sessionId);
}
