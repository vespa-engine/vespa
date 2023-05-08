// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HostInfo information only returned by content nodes (i.e. search nodes)
 */
public class ContentNode {
    @JsonProperty("resource-usage")
    private final Map<String, ResourceUsage> resourceUsage = new HashMap<>();

    public Map<String, ResourceUsage> getResourceUsage() {
        return Collections.unmodifiableMap(resourceUsage);
    }

    public Optional<ResourceUsage> resourceUsageOf(String type) {
        return Optional.ofNullable(resourceUsage.get(type));
    }
}
