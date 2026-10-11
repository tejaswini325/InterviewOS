package com.interviewos.repository;

import com.interviewos.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SessionRepository extends JpaRepository<InterviewSession, Long>, JpaSpecificationExecutor<InterviewSession> {
    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id and s.userId = :userId")
    Optional<InterviewSession> lockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id")
    Optional<InterviewSession> lockById(@Param("id") Long id);

    Optional<InterviewSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(Long userId, SessionStatus status);
    List<InterviewSession> findByStatusAndDeadlineAtBefore(SessionStatus status, Instant cutoff);
    List<InterviewSession> findByUserIdAndStatusOrderByStartedAtAsc(Long userId, SessionStatus status);
    long countByUserId(Long userId);
    long countByUserIdAndStatus(Long userId, SessionStatus status);
}
