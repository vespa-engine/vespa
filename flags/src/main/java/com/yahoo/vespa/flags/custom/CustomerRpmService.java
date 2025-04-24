// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

/***
 * Represents a single customer RPM service running on a host.
 *
 * @author Sigve Røkenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class CustomerRpmService {

    /**
     * The identifier or name of the service unit, typically used in systemd,
     * for instance {@code my-service.service}.
     */
    @JsonProperty(value = "unit", required = true)
    private final String unit;

    /**
     * The download URL of the RPM package associated with the service unit.
     */
    @JsonProperty(value = "url", required = true)
    private final String url;

    /**
     * Memory limit in megabytes (MB) for the service unit.
     * This limit is enforced by the operating system via the systemd cgroup.
     */
    @JsonProperty(value = "memoryLimitMb", required = true)
    private final double memoryLimitMb;

    /**
     * Optional CPU usage limit in nanoseconds for the service unit.
     * Controlled by systemd's cgroup configuration. Null indicates no limit.
     */
    @JsonProperty("cpuLimitNanoSeconds")
    private final Double cpuLimitNanoSeconds;

    @JsonCreator
    public CustomerRpmService(
        @JsonProperty(value = "unit", required = true) String unit,
        @JsonProperty(value = "url", required = true) String url,
        @JsonProperty(value = "memoryLimitMb", required = true) double memoryLimitMb,
        @JsonProperty("cpuLimitNanoSeconds") Double cpuLimitNanoSeconds
    ) {
        this.unit = Objects.requireNonNull(unit);
        this.url = Objects.requireNonNull(url);
        this.memoryLimitMb = memoryLimitMb;
        this.cpuLimitNanoSeconds = cpuLimitNanoSeconds;
    }

    public String unit() {
        return unit;
    }

    public String url() {
        return url;
    }

    public double memoryLimitMb() {
        return memoryLimitMb;
    }

    public Optional<Double> cpuLimitNanoSeconds() {
        return Optional.ofNullable(cpuLimitNanoSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerRpmService other = (CustomerRpmService) o;
        return
            unit.equals(other.unit) &&
            url.equals(other.url) &&
            memoryLimitMb == other.memoryLimitMb &&
            cpuLimitNanoSeconds().equals(other.cpuLimitNanoSeconds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, url, memoryLimitMb, cpuLimitNanoSeconds);
    }

}
