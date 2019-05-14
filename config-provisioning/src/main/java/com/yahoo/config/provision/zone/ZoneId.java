// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;

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

    // TODO: Remove static factory methods not specifying cloud and system

    private final Environment environment;
    private final RegionName region;
    private final CloudName cloud;
    private final SystemName system;

    private ZoneId(Environment environment, RegionName region, CloudName cloud, SystemName system) {
        this.environment = Objects.requireNonNull(environment, "environment must be non-null");
        this.region = Objects.requireNonNull(region, "region must be non-null");
        this.cloud = Objects.requireNonNull(cloud, "cloud must be non-null");
        this.system = Objects.requireNonNull(system, "system must be non-null");
    }

    private ZoneId(Environment environment, RegionName region) {
        this(environment, region, CloudName.defaultName(), SystemName.defaultSystem());
    }

    public static ZoneId from(Environment environment, RegionName region) {
        return new ZoneId(environment, region);
    }

    public static ZoneId from(Environment environment, RegionName region, CloudName cloud, SystemName system) {
        return new ZoneId(environment, region, cloud, system);
    }

    public static ZoneId from(String environment, String region) {
        return from(Environment.from(environment), RegionName.from(region));
    }

    /** Create from a serialised ZoneId. Inverse of {@code ZoneId.value()}. */
    public static ZoneId from(String value) {
        String[] parts = value.split("\\.");
        switch (parts.length) {
            case 2:
                return from(parts[0], parts[1]);
            case 4:
                return from(parts[2], parts[3], parts[0], parts[1]);
            default:
                throw new IllegalArgumentException("Cannot deserialize zone id '" + value + "'");
        }
    }

    public static ZoneId from(Environment environment, RegionName region, CloudName cloud) {
        return new ZoneId(environment, region, cloud, SystemName.defaultSystem());
    }

    public static ZoneId from(String environment, String region, String cloud) {
        return new ZoneId(Environment.from(environment), RegionName.from(region), CloudName.from(cloud), SystemName.defaultSystem());
    }

    public static ZoneId from(String environment, String region, String cloud, String system) {
        return new ZoneId(Environment.from(environment), RegionName.from(region), CloudName.from(cloud), SystemName.from(system));
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

    public SystemName system() {
        return system;
    }

    /** Returns the serialised value of this. Inverse of {@code ZoneId.from(String value)}. */
    public String value() {
        return environment + "." + region;
        // TODO: Change to the below when there only methods use constructor including cloud and system are used and
        // all serialized values contain cloud and system
        // return cloud + "." + system + "." + environment + "." + region;
    }

    @Override
    public String toString() {
        return "zone " + value() + " in " + cloud;
        // TODO: Use the below (need  to fix some use of toString() in tests first)
        //return "zone " + cloud + "." + system + "." + environment + "." + region;
    }

    @Override
    // TODO: Update to check cloud and system when everyone use methods that specify cloud and system
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

