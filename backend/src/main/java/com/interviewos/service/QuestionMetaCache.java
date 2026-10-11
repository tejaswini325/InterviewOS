package com.interviewos.service;

import com.interviewos.domain.*;
import com.interviewos.repository.QuestionRepository;
import java.io.Serializable;
import java.util.ArrayList;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Redis-cached lightweight question metadata used on every question selection (the bank is read-mostly). */
@Component
public class QuestionMetaCache {
    public record QuestionMeta(Long id, Topic topic, Difficulty difficulty, QuestionType type) implements Serializable {}

    private final QuestionRepository repo;

    public QuestionMetaCache(QuestionRepository repo) { this.repo = repo; }

    @Cacheable(cacheNames = "questionMeta", key = "'all'")
    @Transactional(readOnly = true)
    public ArrayList<QuestionMeta> allActive() {
        ArrayList<QuestionMeta> out = new ArrayList<>();
        for (Question q : repo.findByActiveTrue()) out.add(new QuestionMeta(q.getId(), q.getTopic(), q.getDifficulty(), q.getType()));
        return out;
    }
}
