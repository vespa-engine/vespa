// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.component.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An OS release and its tag.
 *
 * @author mpolden
 */
public record OsRelease(Version version, Tag tag, Instant taggedAt) {

    public OsRelease {
        Objects.requireNonNull(version);
        Objects.requireNonNull(tag);
        Objects.requireNonNull(taggedAt);
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

    /** Returns the age of this at given instant */
    public Duration age(Instant instant) {
        return Duration.between(taggedAt, instant);
    }

    @Override
    public String toString() {
        return "os release " + version + ", tagged " + tag + " at " + taggedAt;
    }

    /** Known release tags */
    public enum Tag {
        latest,
        stable,
    }

}
