// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * Represents an applications region, which may be any kind of string or default. This type is defined
 * in order to provide a type safe API for defining regions.
 *
 * @author Ulf Lilleengen
 * @since 5.11
 */
public class RegionName implements Comparable<RegionName> {

    private final String region;

    private RegionName(String region) {
        this.region = region;
    }

    @Override
    public int hashCode() {
        return region.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RegionName)) return false;
        return Objects.equals(((RegionName) obj).region, region);
    }

    @Override
    public String toString() {
        return region;
    }

    // TODO: Add verification of region name.
    public static RegionName from(String region) {
        return new RegionName(region);
    }

    public static RegionName defaultName() {
        return new RegionName("default");
    }

    public boolean isDefault() {
        return equals(RegionName.defaultName());
    }

    public String value() { return region; }

    @Override
    public int compareTo(RegionName region) {
        return this.region.compareTo(region.region);
    }
}
