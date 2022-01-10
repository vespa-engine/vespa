// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * An OS release and its tag.
 *
 * @author mpolden
 */
public class OsRelease {

    private final Version version;
    private final Tag tag;
    private final Instant taggedAt;

    public OsRelease(Version version, Tag tag, Instant taggedAt) {
        this.version = Objects.requireNonNull(version);
        this.tag = Objects.requireNonNull(tag);
        this.taggedAt = Objects.requireNonNull(taggedAt);
    }

    /** The version number */
    public Version version() {
        return version;
    }

    /** The tag of this */
    public Tag tag() {
        return tag;
    }

    /** Returns the time this was tagged */
    public Instant taggedAt() {
        return taggedAt;
    }

    @Override
    public String toString() {
        return "os release " + version + ", tagged " + tag + " at " + taggedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OsRelease osRelease = (OsRelease) o;
        return version.equals(osRelease.version) && tag == osRelease.tag && taggedAt.equals(osRelease.taggedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, tag, taggedAt);
    }

    /** Known release tags */
    public enum Tag {
        latest,
        stable,
    }

}
