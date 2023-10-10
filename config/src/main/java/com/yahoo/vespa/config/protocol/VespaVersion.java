// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

/**
 * A wrapper class for Vespa version
 *
 * @author hmusum
 */
public class VespaVersion {

    private final String version;

    public static VespaVersion fromString(String version) {
        return new VespaVersion(version);
    }

    private VespaVersion(String version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VespaVersion that = (VespaVersion) o;

        if (!version.equals(that.version)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public String toString() {
        return version;
    }

}
