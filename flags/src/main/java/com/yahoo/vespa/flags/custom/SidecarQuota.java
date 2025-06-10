// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class SidecarQuota {
    private final Double cpu;
    private final Double memoryGiB;
    private final String gpu;

    @JsonCreator
    public SidecarQuota(
            @JsonProperty("cpu") Double cpu,
            @JsonProperty("memory") Double memoryGiB,
            @JsonProperty("gpu") String gpu) {
        this.cpu = cpu;
        this.memoryGiB = memoryGiB;
        this.gpu = gpu;
    }

    @JsonGetter("cpu")
    public Double getCpu() {
        return cpu;
    }

    @JsonGetter("memoryGiB")
    public Double getMemoryGiB() {
        return memoryGiB;
    }

    @JsonGetter("gpu")
    public String getGpu() {
        return gpu;
    }

    @Override
    public String toString() {
        return "SidecarQuota{cpus=%s, memoryGiB=%s, gpu='%s'}"
                .formatted(
                        Optional.ofNullable(cpu).map(Object::toString).orElse("null"),
                        Optional.ofNullable(memoryGiB).map(Object::toString).orElse("null"),
                        String.valueOf(gpu));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (SidecarQuota) o;
        return Objects.equals(cpu, that.cpu)
                && Objects.equals(memoryGiB, that.memoryGiB)
                && Objects.equals(gpu, that.gpu);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpu, memoryGiB, gpu);
    }
}
