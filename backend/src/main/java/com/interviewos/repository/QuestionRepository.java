package com.interviewos.repository;

import com.interviewos.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByActiveTrue();
    List<Question> findByPersonalizedSessionIdOrderByPersonalizedPositionAsc(Long personalizedSessionId);
}
