// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Objects;

/**
 * An OS version for a cloud in this system.
 *
 * @author mpolden
 */
public class OsVersion implements Comparable<OsVersion> {

    private static final Comparator<OsVersion> comparator = Comparator.comparing(OsVersion::cloud)
                                                                      .thenComparing(OsVersion::version);

    private final Version version;
    private final CloudName cloud;

    public OsVersion(Version version, CloudName cloud) {
        this.version = Objects.requireNonNull(version, "version must be non-null");
        this.cloud = Objects.requireNonNull(cloud, "cloud must be non-null");
    }

    /** The version number of this */
    public Version version() {
        return version;
    }

    /** The cloud where this OS version is used */
    public CloudName cloud() {
        return cloud;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsVersion osVersion = (OsVersion) o;
        return Objects.equals(version, osVersion.version) &&
               Objects.equals(cloud, osVersion.cloud);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, cloud);
    }

    @Override
    public String toString() {
        return "version " + version + " for " + cloud + " cloud";
    }

    @Override
    public int compareTo(@NotNull OsVersion that) {
        return comparator.compare(this, that);
    }

}
