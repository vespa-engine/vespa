// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.component.Version;

import java.util.Objects;
import java.util.Optional;

/**
 * The OS version of a node. This contains the current and wanted OS version and is immutable.
 *
 * @author mpolden
 */
public class OsVersion {

    public static final OsVersion EMPTY = new OsVersion(Optional.empty(), Optional.empty());

    private final Optional<Version> current;
    private final Optional<Version> wanted;

    public OsVersion(Optional<Version> current, Optional<Version> wanted) {
        this.current = current;
        this.wanted = requireNonZero(wanted);
    }

    /** The version this node is currently running, if any */
    public Optional<Version> current() {
        return current;
    }

    /** The version this node should upgrade to, if any */
    public Optional<Version> wanted() {
        return wanted;
    }

    /** Returns whether this node is currently changing its version to the given version */
    public boolean changingTo(Version version) {
        return changing() && wanted.get().equals(version);
    }

    /** Returns whether this node is currently changing its version */
    public boolean changing() {
        return wanted.isPresent() && !current.equals(wanted);
    }

    /** Returns whether this is before the given version */
    public boolean isBefore(Version version) {
        return current.isEmpty() || current.get().isBefore(version);
    }

    /** Returns whether current version matches given version */
    public boolean matches(Version version) {
        return current.isPresent() && current.get().equals(version);
    }

    /** Returns a copy of this with current version set to given version */
    public OsVersion withCurrent(Optional<Version> version) {
        return new OsVersion(version, wanted);
    }

    /** Returns a copy of this with wanted version set to given version */
    public OsVersion withWanted(Optional<Version> version) {
        return new OsVersion(current, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsVersion osVersion = (OsVersion) o;
        return current.equals(osVersion.current) &&
               wanted.equals(osVersion.wanted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(current, wanted);
    }

    @Override
    public String toString() {
        return "OS version " + current.map(Version::toFullString).orElse("<unset>") + " [wanted: " +
               wanted.map(Version::toFullString).orElse("<unset>") + "]";
    }

    private static Optional<Version> requireNonZero(Optional<Version> version) {
        Objects.requireNonNull(version, "version must be non-null");
        if (version.isEmpty()) return version;
        if (version.get().isEmpty()) throw new IllegalArgumentException("version must be non-zero");
        return version;
    }

}
