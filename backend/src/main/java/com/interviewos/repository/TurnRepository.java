package com.interviewos.repository;

import com.interviewos.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TurnRepository extends JpaRepository<InterviewTurn, Long> {
    List<InterviewTurn> findBySessionIdOrderById(Long sessionId);
    List<InterviewTurn> findByInterviewQuestionIdOrderById(Long interviewQuestionId);
    Optional<InterviewTurn> findByInterviewQuestionIdAndKind(Long interviewQuestionId, TurnKind kind);
}
