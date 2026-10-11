package com.interviewos.evaluation;

import java.util.List;

/**
 * Port for sandboxed code execution. Candidate code must NEVER run inside the Spring Boot JVM.
 * A real implementation must call an isolated runner (separate container/VM, no network, read-only FS,
 * CPU/memory/pids/time limits). None is bundled: see docs/ARCHITECTURE.md.
 */
public interface CodeExecutor {
    boolean available();
    ExecutionResult run(String language, String source, List<TestCase> tests);

    record TestCase(String input, String expected) {}
    record ExecutionResult(int passed, int total, String message) {}
}
