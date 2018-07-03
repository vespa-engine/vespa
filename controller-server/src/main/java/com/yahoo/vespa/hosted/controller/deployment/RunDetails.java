package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

/**
 * Contains details about a deployment job run.
 *
 * @author jonmv
 */
public class RunDetails {

    private final Map<Step, byte[]> logs;

    public RunDetails(Map<Step, byte[]> logs) {
        this.logs = ImmutableMap.copyOf(logs);
    }

    public Optional<byte[]> get(Step step) {
        return Optional.ofNullable(logs.get(step));
    }

}
