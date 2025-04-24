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

    @JsonProperty(value = "url", required = true)
    String url;

    @JsonProperty(value = "memoryLimitMb", required = true)
    double memoryLimitMb;

    @JsonProperty("cpuLimitNanoSeconds")
    Double cpuLimitNanoSeconds;

    @JsonCreator
    public CustomerRpmService(
        @JsonProperty(value = "url", required = true) String url,
        @JsonProperty(value = "memoryLimitMb", required = true) double memoryLimitMb,
        @JsonProperty("cpuLimitNanoSeconds") Double cpuLimitNanoSeconds
    ) {
        this.url = Objects.requireNonNull(url);
        this.memoryLimitMb = memoryLimitMb;
        this.cpuLimitNanoSeconds = cpuLimitNanoSeconds;
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
            url.equals(other.url) &&
            memoryLimitMb == other.memoryLimitMb &&
            cpuLimitNanoSeconds().equals(other.cpuLimitNanoSeconds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, memoryLimitMb, cpuLimitNanoSeconds);
    }

}
