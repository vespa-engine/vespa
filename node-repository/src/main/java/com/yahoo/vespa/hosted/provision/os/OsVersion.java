// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;

import java.util.Objects;

/**
 * An OS version and it's active status.
 *
 * @author mpolden
 */
public class OsVersion {

    private final Version version;
    private final boolean active;

    public OsVersion(Version version, boolean active) {
        this.version = version;
        this.active = active;
    }

    /** The OS version number */
    public Version version() {
        return version;
    }

    /** Returns whether this is currently active and should be acted on by nodes */
    public boolean active() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsVersion osVersion = (OsVersion) o;
        return active == osVersion.active &&
               version.equals(osVersion.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, active);
    }

    @Override
    public String toString() {
        return "OS version " + version + " [active: " + active + "]";
    }

}
