package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.util.List;
import java.util.logging.Level;

import static java.util.Objects.requireNonNull;

/**
 * The result of a deployment, carried out against a {@link ConfigServer}.
 *
 * @author jonmv
 */
public record DeploymentResult(String message, List<LogEntry> log) {

    public DeploymentResult {
        requireNonNull(message);
        requireNonNull(log);
    }

    public record LogEntry(long epochMillis, String message, Level level, boolean concernsPackage) {

        public LogEntry {
            requireNonNull(message);
            requireNonNull(level);
        }

    }

}
