// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;

import java.util.Comparator;
import java.util.Objects;

/**
 * An OS version for a cloud in this system.
 *
 * @author mpolden
 */
public record OsVersion(Version version, CloudName cloud) implements Comparable<OsVersion> {

    private static final Comparator<OsVersion> comparator = Comparator.comparing(OsVersion::cloud)
                                                                      .thenComparing(OsVersion::version);

    public OsVersion {
        Objects.requireNonNull(version, "version must be non-null");
        Objects.requireNonNull(cloud, "cloud must be non-null");
    }

    @Override
    public String toString() {
        return "version " + version.toFullString() + " for " + cloud + " cloud";
    }

    @Override
    public int compareTo(OsVersion that) {
        return comparator.compare(this, that);
    }

}
