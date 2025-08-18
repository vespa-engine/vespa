// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashSet;

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

        if (maxCpu != 0 && minCpu != 0 && maxCpu < minCpu) {
            throw new IllegalArgumentException(
                    "Non-zero maxCpu must be greater than or equal to non-zero minCpu, actual %s and %s".formatted(maxCpu, minCpu));
        }

        if (memoryGiB < 0) {
            throw new IllegalArgumentException("memoryGiB must be non-negative, actual %s".formatted(memoryGiB));
        }

        if (gpu != null && !gpu.equals("all")) {
            try {
                var indexes = new HashSet<Integer>();

                for (var indexStr : gpu.split(",", -1)) {
                    var trimmed = indexStr.trim();

                    if (trimmed.isEmpty()) {
                        throw new IllegalArgumentException(
                                "GPU device indexes can't be empty, actual: %s".formatted(gpu));
                    }

                    int index = Integer.parseInt(trimmed);

                    if (index < 0) {
                        throw new IllegalArgumentException(
                                "GPU device indexes must be non-negative, actual: %s".formatted(gpu));
                    }

                    if (!indexes.add(index)) {
                        throw new IllegalArgumentException("GPU device indexes contain duplicates: %s".formatted(gpu));
                    }
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "GPU must be null, \"all\", or comma-separated list of device indexes, actual: %s".formatted(
                                gpu));
            }
        }
    }
}
