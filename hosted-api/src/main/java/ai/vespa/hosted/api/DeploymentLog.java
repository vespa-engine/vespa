// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

/**
 * A list of {@link Entry} items from a deployment job.
 *
 * @author jonmv
 */
public class DeploymentLog {

    private final List<Entry> entries;
    private final boolean active;
    private final Status status;
    private final OptionalLong last;

    public DeploymentLog(List<Entry> entries, boolean active, Status status, OptionalLong last) {
        this.entries = entries.stream().sorted(comparing(Entry::at)).toList();
        this.active = active;
        this.status = status;
        this.last = last;
    }

    /** Returns this log updated with the content of the other. */
    public DeploymentLog updatedWith(DeploymentLog other) {
        return new DeploymentLog(Stream.concat(entries.stream(), other.entries.stream()).toList(),
                                 other.active,
                                 other.status,
                                 other.last);
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isActive() {
        return active;
    }

    public Status status() {
        return status;
    }

    public OptionalLong last() {
        return last;
    }

    @Override
    public String toString() {
        return "status: " + status.name() + ", " + (active ? "active" : "not active") + ", log entries:\n" +
                entries.stream()
                       .map(entry -> String.format("%s %s %s", entry.at(), entry.level(), entry.message()))
                       .collect(Collectors.joining("\n"));
    }

    public static class Entry {

        private final Instant at;
        private final Level level;
        private final String message;
        private final boolean isVespaLogEntry;

        public Entry(Instant at, Level level, String message, boolean isVespaLogEntry) {
            this.at = at;
            this.level = level;
            this.message = message;
            this.isVespaLogEntry = isVespaLogEntry;
        }

        public Instant at() {
            return at;
        }

        public Level level() {
            return level;
        }

        public String message() {
            return message;
        }

        public boolean isVespaLogEntry() {
            return isVespaLogEntry;
        }

    }


    public enum Level {
        error,
        warning,
        info,
        debug;

        public static Level of(String level) {
            return switch (level) {
                case "error" -> error;
                case "warning" -> warning;
                case "info" -> info;
                case "debug" -> debug;
                default -> debug;
            };
        }
    }


    public enum Status {
        running,
        aborted,
        error,
        testFailure,
        nodeAllocationFailure,
        installationFailed,
        deploymentFailed,
        endpointCertificateTimeout,
        success;
    }

}
