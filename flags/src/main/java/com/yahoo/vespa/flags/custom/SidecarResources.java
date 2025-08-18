// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

// defaultValue = "0" means unlimited
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record SidecarResources(Double maxCpu, Double minCpu, Double memoryGiB, String gpu) {
    public static SidecarResources DEFAULT = new SidecarResources(0.0, 0.0, 0.0, null);

    public SidecarResources {
        maxCpu = maxCpu == null ? 0.0 : maxCpu;
        minCpu = minCpu == null ? 0.0 : minCpu;
        memoryGiB = memoryGiB == null ? 0.0 : memoryGiB;
    }
}
