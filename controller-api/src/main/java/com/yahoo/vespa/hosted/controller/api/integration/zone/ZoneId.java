// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.util.Objects;

/**
 * Unique identifier for a Zone; use when referencing them.
 *
 * Serialised form is 'environment.region'.
 *
 * @author jonmv
 */
public class ZoneId {
    // TODO: Replace usages of environment + region with usages of this.

    private final Environment environment;
    private final RegionName region;
    private final CloudName cloud;

    private ZoneId(Environment environment, RegionName region, CloudName cloud) {
        this.environment = Objects.requireNonNull(environment, "environment must be non-null");
        this.region = Objects.requireNonNull(region, "region must be non-null");
        this.cloud = Objects.requireNonNull(cloud, "cloud must be non-null");
    }

    private ZoneId(Environment environment, RegionName region) {
        this(environment, region, CloudName.defaultName());
    }

    public static ZoneId from(Environment environment, RegionName region) {
        return new ZoneId(environment, region);
    }

    public static ZoneId from(String environment, String region) {
        return from(Environment.from(environment), RegionName.from(region));
    }

    /** Create from a serialised ZoneId. Inverse of {@code ZoneId.value()}. */
    public static ZoneId from(String value) {
        String[] parts = value.split("\\.");
        return from(parts[0], parts[1]);
    }

    public static ZoneId from(Environment environment, RegionName region, CloudName cloud) {
        return new ZoneId(environment, region, cloud);
    }

    public static ZoneId from(String environment, String region, String cloud) {
        return new ZoneId(Environment.from(environment), RegionName.from(region), CloudName.from(cloud));
    }

    public Environment environment() {
        return environment;
    }

    public RegionName region() {
        return region;
    }

    public CloudName cloud() {
        return cloud;
    }

    /** Returns the serialised value of this. Inverse of {@code ZoneId.from(String value)}. */
    public String value() {
        return environment + "." + region;
    }

    @Override
    public String toString() {
        return "zone " + value() + " in " + cloud;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZoneId zoneId = (ZoneId) o;
        return environment == zoneId.environment &&
               Objects.equals(region, zoneId.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(environment, region);
    }

}

