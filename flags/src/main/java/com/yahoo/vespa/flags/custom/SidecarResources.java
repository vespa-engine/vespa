// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record SidecarResources(double maxCpu, double minCpu, double memoryGiB, String gpu) {
    // 0.0 means unlimited, gpu = null means no GPU
    public static SidecarResources DEFAULT = new SidecarResources(0.0, 0.0, 0.0, null);

    public SidecarResources {
        if (maxCpu < 0) {
            throw new IllegalArgumentException("maxCpu must be non-negative, actual %s".formatted(maxCpu));
        }

        if (minCpu < 0) {
            throw new IllegalArgumentException("minCpu must be non-negative, actual %s".formatted(minCpu));
        }

        if (maxCpu < minCpu) {
            throw new IllegalArgumentException(
                    "maxCpu must be greater than or equal to minCpu, actual %s and %s".formatted(maxCpu, minCpu));
        }
        
        if (memoryGiB < 0) {
            throw new IllegalArgumentException("memoryGiB must be non-negative, actual %s".formatted(memoryGiB));
        }
    }
}
