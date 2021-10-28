// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.host;

import java.util.Objects;
import java.util.Optional;

/**
 * Overrides fields in a {@link com.yahoo.config.provision.Flavor}, e.g. when a flavor is not
 * tied to a specific disk.
 *
 * @author freva
 */
public class FlavorOverrides {

    private final Optional<Double> diskGb;

    private FlavorOverrides(Optional<Double> diskGb) {
        this.diskGb = diskGb;
    }

    public Optional<Double> diskGb() {
        return diskGb;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlavorOverrides that = (FlavorOverrides) o;
        return diskGb.equals(that.diskGb);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diskGb);
    }

    @Override
    public String toString() {
        return "[" + diskGb.map(d -> "disk " + d + " Gb").orElse("") + "]";
    }

    public static FlavorOverrides ofDisk(double diskGb) {
        return new FlavorOverrides(Optional.of(diskGb));
    }

}
