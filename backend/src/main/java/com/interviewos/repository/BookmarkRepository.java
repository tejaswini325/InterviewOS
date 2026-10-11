package com.interviewos.repository;

import com.interviewos.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Bookmark> findByUserIdAndQuestionId(Long userId, Long questionId);
}
