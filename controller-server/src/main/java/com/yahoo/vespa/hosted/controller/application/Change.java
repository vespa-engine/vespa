// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

/**
 * The changes to an application we currently wish to complete deploying.
 * A goal of the system is to deploy platform and application versions separately.
 * However, this goal must some times be traded against others, so a change can
 * consist of both an application and platform version change.
 *
 * This is immutable.
 *
 * @author bratseth
 */
public final class Change {

    private static final Change empty = new Change(Optional.empty(), Optional.empty(), false);

    /** The platform version we are upgrading to, or empty if none */
    private final Optional<Version> platform;

    /** The application version we are changing to, or empty if none */
    private final Optional<RevisionId> revision;

    /** Whether this change is a pin to its contained Vespa version, or to the application's current. */
    private final boolean pinned;

    private Change(Optional<Version> platform, Optional<RevisionId> revision, boolean pinned) {
        this.platform = requireNonNull(platform, "platform cannot be null");
        this.revision = requireNonNull(revision, "revision cannot be null");
        if (revision.isPresent() && ( ! revision.get().isProduction())) {
            throw new IllegalArgumentException("Application version to deploy must be a known version");
        }
        this.pinned = pinned;
    }

    public Change withoutPlatform() {
        return new Change(Optional.empty(), revision, pinned);
    }

    public Change withoutApplication() {
        return new Change(platform, Optional.empty(), pinned);
    }

    /** Returns whether a change should currently be deployed */
    public boolean hasTargets() {
        return platform.isPresent() || revision.isPresent();
    }

    /** Returns whether this is the empty change. */
    public boolean isEmpty() {
        return ! hasTargets() && ! pinned;
    }

    /** Returns the platform version carried by this. */
    public Optional<Version> platform() { return platform; }

    /** Returns the application version carried by this. */
    public Optional<RevisionId> revision() { return revision; }

    public boolean isPinned() { return pinned; }

    /** Returns an instance representing no change */
    public static Change empty() { return empty; }

    /** Returns a version of this change which replaces or adds this platform change */
    public Change with(Version platformVersion) {
        if (pinned)
            throw new IllegalArgumentException("Not allowed to set a platform version when pinned.");

        return new Change(Optional.of(platformVersion), revision, pinned);
    }

    /** Returns a version of this change which replaces or adds this revision change */
    public Change with(RevisionId revision) {
        return new Change(platform, Optional.of(revision), pinned);
    }

    /** Returns a change with the versions of this, and with the platform version pinned. */
    public Change withPin() {
        return new Change(platform, revision, true);
    }

    /** Returns a change with the versions of this, and with the platform version unpinned. */
    public Change withoutPin() {
        return new Change(platform, revision, false);
    }

    /** Returns the change obtained when overwriting elements of the given change with any present in this */
    public Change onTopOf(Change other) {
        if (platform.isPresent())
            other = other.with(platform.get());
        if (revision.isPresent())
            other = other.with(revision.get());
        if (pinned)
            other = other.withPin();
        return other;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Change)) return false;
        Change change = (Change) o;
        return pinned == change.pinned &&
               Objects.equals(platform, change.platform) &&
               Objects.equals(revision, change.revision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, revision, pinned);
    }

    @Override
    public String toString() {
        StringJoiner changes = new StringJoiner(" and ");
        if (pinned)
            changes.add("pin to " + platform.map(Version::toString).orElse("current platform"));
        else
            platform.ifPresent(version -> changes.add("upgrade to " + version));
        revision.ifPresent(revision -> changes.add("revision change to " + revision));
        changes.setEmptyValue("no change");
        return changes.toString();
    }

    public static Change of(RevisionId revision) {
        return new Change(Optional.empty(), Optional.of(revision), false);
    }

    public static Change of(Version platformChange) {
        return new Change(Optional.of(platformChange), Optional.empty(), false);
    }

    /** Returns whether this change carries a revision downgrade relative to the given revision. */
    public boolean downgrades(RevisionId revision) {
        return this.revision.map(revision::compareTo).orElse(0) > 0;
    }

    /** Returns whether this change carries a platform downgrade relative to the given version. */
    public boolean downgrades(Version version) {
        return platform.map(version::compareTo).orElse(0) > 0;
    }

    /** Returns whether this change carries a revision upgrade relative to the given  revision. */
    public boolean upgrades(RevisionId revision) {
        return this.revision.map(revision::compareTo).orElse(0) < 0;
    }

    /** Returns whether this change carries a platform upgrade relative to the given version. */
    public boolean upgrades(Version version) {
        return platform.map(version::compareTo).orElse(0) < 0;
    }

}

