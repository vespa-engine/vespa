// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;

import java.time.Instant;
import java.util.Objects;

/**
 * A controller's Vespa version and commit details.
 *
 * @author mpolden
 */
public class ControllerVersion implements Comparable<ControllerVersion> {

    /** The current version of this controller */
    public static final ControllerVersion CURRENT = new ControllerVersion(Vtag.currentVersion, Vtag.commitSha, Vtag.commitDate);

    private final Version version;
    private final String commitSha;
    private final Instant commitDate;

    public ControllerVersion(Version version, String commitSha, Instant commitDate) {
        this.version = Objects.requireNonNull(version);
        this.commitSha = Objects.requireNonNull(commitSha);
        this.commitDate = Objects.requireNonNull(commitDate);
    }

    /** Vespa version */
    public Version version() {
        return version;
    }

    /** Commit SHA of this */
    public String commitSha() {
        return commitSha;
    }

    /** The time this was committed */
    public Instant commitDate() {
        return commitDate;
    }

    @Override
    public String toString() {
        return version + ", commit " + commitSha + " @ " + commitDate;
    }

    @Override
    public int compareTo(ControllerVersion o) {
        return version.compareTo(o.version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ControllerVersion that = (ControllerVersion) o;
        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }

}
