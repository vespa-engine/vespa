// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Immutable class which contains the log of a deployment job run.
 *
 * @author jonmv
 */
public class RunLog {

    private static final RunLog empty = RunLog.of(Collections.emptyMap());

    private final Map<Step, List<LogEntry>> log;
    private final OptionalLong lastId;

    private RunLog(OptionalLong lastId, Map<Step, List<LogEntry>> log) {
        this.log = log;
        this.lastId = lastId;
    }

    /** Creates a RunLog which contains a deep copy of the given log. */
    public static RunLog of(Map<Step, List<LogEntry>> log) {
        ImmutableMap.Builder<Step, List<LogEntry>> builder = ImmutableMap.builder();
        log.forEach((step, entries) -> {
            if ( ! entries.isEmpty())
                builder.put(step, ImmutableList.copyOf(entries));
        });
        OptionalLong lastId = log.values().stream()
                                 .flatMap(List::stream)
                                 .mapToLong(LogEntry::id)
                                 .max();
        return new RunLog(lastId, builder.build());
    }

    /** Returns an empty RunLog. */
    public static RunLog empty() {
        return empty;
    }

    /** Returns the log entries for the given step, if any are recorded. */
    public List<LogEntry> get(Step step) {
        return log.getOrDefault(step, Collections.emptyList());
    }

    /** Returns the id of the last log entry in this, if it has any. */
    public OptionalLong lastId() {
        return lastId;
    }

}
