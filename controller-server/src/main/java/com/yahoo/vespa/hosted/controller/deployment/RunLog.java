package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Immutable class which contains details about a deployment job run.
 *
 * @author jonmv
 */
public class RunDetails {

    private static final RunDetails empty = RunDetails.of(Collections.emptyMap());

    private final Map<Step, List<LogEntry>> log;
    private final OptionalLong lastId;

    private RunDetails(OptionalLong lastId, Map<Step, List<LogEntry>> log) {
        this.log = log;
        this.lastId = lastId;
    }

    /** Creates a RunDetails which contains a deep copy of the given logs. */
    public static RunDetails of(Map<Step, List<LogEntry>> logs) {
        ImmutableMap.Builder<Step, List<LogEntry>> builder = ImmutableMap.builder();
        logs.forEach((step, entries) -> builder.put(step, ImmutableList.copyOf(entries)));
        OptionalLong lastId = logs.values().stream()
                                  .flatMap(List::stream)
                                  .mapToLong(LogEntry::id)
                                  .max();
        return new RunDetails(lastId, builder.build());
    }

    /** Returns an empty RunDetails. */
    public static RunDetails empty() {
        return empty;
    }

    /** Returns the log entries for the given step, if any are recorded. */
    public Optional<List<LogEntry>> get(Step step) {
        return Optional.ofNullable(log.get(step));
    }

    /** Returns the id of the last log entry in this. */
    public OptionalLong lastId() {
        return lastId;
    }

}
