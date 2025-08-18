// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record Sidecars(
        List<Sidecar> sidecars
) {
    public Sidecars {
        if (sidecars == null) {
            sidecars = List.of();
        }
        
        Set<Integer> uniqueIds = sidecars.stream()
                .map(Sidecar::id)
                .collect(Collectors.toSet());
        if (uniqueIds.size() != sidecars.size()) {
            throw new IllegalArgumentException("Sidecar IDs must be unique");
        }
    }

    public static Sidecars DEFAULT = new Sidecars(List.of());
}
