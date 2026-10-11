package com.interviewos.repository;

import com.interviewos.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SkillScoreRepository extends JpaRepository<SkillScore, Long> {
    Optional<SkillScore> findByUserIdAndTopic(Long userId, Topic topic);
    List<SkillScore> findByUserIdOrderByTopic(Long userId);
}
