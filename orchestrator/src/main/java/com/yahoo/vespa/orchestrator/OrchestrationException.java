// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import java.util.Arrays;

public class OrchestrationException extends RuntimeException {

    public OrchestrationException(Throwable cause) {
        super(cause);
    }

    public OrchestrationException(String message) {
        super(message);
    }

    public OrchestrationException(String message, Throwable cause) {
        super(message, cause);
    }

    // Overrides getMessage() to include suppressed Throwables, which is useful to see which
    // "resumes" of a failed suspend succeeded.
    @Override
    public String getMessage() {
        StringBuilder builder = new StringBuilder();
        builder.append(super.getMessage());
        Throwable[] suppressedThrowables = getSuppressed();
        Arrays.stream(suppressedThrowables).forEach(t -> builder.append("; With suppressed throwable " + t));
        return builder.toString();
    }

}
