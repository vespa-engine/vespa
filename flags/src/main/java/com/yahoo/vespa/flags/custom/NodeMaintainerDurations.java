// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code NodeRepositoryMaintenance} depends on various durations, e.g. the reboot interval. This class
 * defines a serialization format for specifying such durations as a JSON object with keys matching
 * the name (reboot_interval) and with the value being the duration in second as an integer.
 *
 * @author hakonhall
 */
public class NodeMaintainerDurations {
    @JsonAnySetter
    private final Map<String, Long> durations = new HashMap<>();

    @JsonCreator
    public NodeMaintainerDurations() {}

    public NodeMaintainerDurations(Map<String, Long> durations) { this.durations.putAll(durations); }

    @JsonAnyGetter
    private Map<String, Long> getDurations() { return durations; }

    public Optional<Duration> getDuration(String durationName) {
        return Optional.ofNullable(durations.get(durationName)).map(Duration::ofSeconds);
    }
}
