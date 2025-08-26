// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record Sidecars(@JsonProperty("sidecars") List<Sidecar> sidecars) {
    @JsonCreator
    public Sidecars {
        if (sidecars == null) {
            sidecars = List.of();
        }

        var ids = sidecars.stream().map(Sidecar::id).toList();
        var uniqueIds = new HashSet<>(ids);
        if (ids.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("Sidecar IDs must be unique, actual: %s".formatted(
                    ids.stream().map(String::valueOf).collect(Collectors.joining(", "))));
        }
    }

    public static Sidecars DEFAULT = new Sidecars(List.of());
}
