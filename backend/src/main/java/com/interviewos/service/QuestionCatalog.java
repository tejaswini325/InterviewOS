package com.interviewos.service;

import com.interviewos.domain.*;
import com.interviewos.evaluation.CodeExecutor;
import com.interviewos.service.QuestionMetaCache.QuestionMeta;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class QuestionCatalog {
    public record TopicInfo(Topic topic, int total, Map<Difficulty, Integer> byDifficulty) {}

    private final QuestionMetaCache cache;
    private final CodeExecutor executor;

    public QuestionCatalog(QuestionMetaCache cache, CodeExecutor executor) { this.cache = cache; this.executor = executor; }

    /** Questions that can actually be served: coding questions only when a sandbox is available. */
    public List<QuestionMeta> eligible() {
        return cache.allActive().stream().filter(q -> q.type() != QuestionType.CODING || executor.available()).toList();
    }

    public List<TopicInfo> topics() {
        List<TopicInfo> out = new ArrayList<>();
        for (Topic t : Topic.values()) {
            Map<Difficulty, Integer> m = new EnumMap<>(Difficulty.class);
            for (Difficulty d : Difficulty.values()) m.put(d, 0);
            int total = 0;
            for (QuestionMeta q : eligible()) if (q.topic() == t) { m.merge(q.difficulty(), 1, Integer::sum); total++; }
            out.add(new TopicInfo(t, total, m));
        }
        return out;
    }

    public boolean codingEnabled() { return executor.available(); }
}
