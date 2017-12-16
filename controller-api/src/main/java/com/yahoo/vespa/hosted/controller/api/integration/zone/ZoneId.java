// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.util.Objects;

/**
 * Unique identifier for a Zone; use when referencing them.
 *
 * Serialised form is 'environment.region'.
 *
 * @author jvenstad
 */
public class ZoneId {
    // TODO: Replace usages of environment + region with usages of this.

    private final Environment environment;
    private final RegionName region;

    private ZoneId(Environment environment, RegionName region) {
        this.environment = Objects.requireNonNull(environment);
        this.region = Objects.requireNonNull(region);
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

    public Environment environment() {
        return environment;
    }

    public RegionName region() {
        return region;
    }

    /** Returns the serialised value of this. Inverse of {@code ZoneId.from(String value)}. */
    public String value() {
        return environment + "." + region;
    }

    @Override
    public String toString() {
        return "zone " + value();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof ZoneId)) return false;
        ZoneId id = (ZoneId) o;
        return environment == id.environment &&
               Objects.equals(region, id.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(environment, region);
    }

}

