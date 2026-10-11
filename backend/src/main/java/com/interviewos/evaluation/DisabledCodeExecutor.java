package com.interviewos.evaluation;

import java.util.List;
import org.springframework.stereotype.Component;

/** Default: execution is OFF. Coding questions stay in the bank but are not served to candidates. */
@Component
public class DisabledCodeExecutor implements CodeExecutor {
    @Override public boolean available() { return false; }
    @Override public ExecutionResult run(String language, String source, List<TestCase> tests) {
        throw new UnsupportedOperationException("Code execution is disabled: no sandbox is configured");
    }
}
