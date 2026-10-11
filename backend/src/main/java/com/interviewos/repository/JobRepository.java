package com.interviewos.repository;

import com.interviewos.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface JobRepository extends JpaRepository<EvaluationJob, Long> {
    Optional<EvaluationJob> findByIdempotencyKey(String key);

    @Modifying
    @Query("update EvaluationJob j set j.status = :to, j.attempts = j.attempts + 1, j.updatedAt = :now where j.id = :id and j.status = :from")
    int claim(@Param("id") Long id, @Param("from") JobStatus from, @Param("to") JobStatus to, @Param("now") Instant now);

    List<EvaluationJob> findByStatusAndUpdatedAtBefore(JobStatus status, Instant cutoff);

    @Query("select j from EvaluationJob j where j.sessionId in (select s.id from InterviewSession s where s.userId = :uid) order by j.updatedAt desc")
    List<EvaluationJob> findAllForUser(@Param("uid") Long userId);

    @Query("select j from EvaluationJob j where j.sessionId in (select s.id from InterviewSession s where s.userId = :uid) and j.status = :status order by j.updatedAt desc")
    List<EvaluationJob> findForUserByStatus(@Param("uid") Long userId, @Param("status") JobStatus status);

    List<EvaluationJob> findByStatusOrderByUpdatedAtDesc(JobStatus status);
}
