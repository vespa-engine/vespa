// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

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
    private final Optional<ApplicationVersion> application;

    private final boolean pinning;

    private Change(Optional<Version> platform, Optional<ApplicationVersion> application, boolean pinning) {
        Objects.requireNonNull(platform, "platform cannot be null");
        Objects.requireNonNull(application, "application cannot be null");
        if (application.isPresent() && application.get().isUnknown()) {
            throw new IllegalArgumentException("Application version to deploy must be a known version");
        }
        this.platform = platform;
        this.application = application;
        this.pinning = pinning;
    }

    public Change withoutPlatform() {
        return new Change(Optional.empty(), application, pinning);
    }

    public Change withoutApplication() {
        return new Change(platform, Optional.empty(), pinning);
    }

    /** Returns whether a change should currently be deployed */
    public boolean isPresent() {
        return platform.isPresent() || application.isPresent();
    }

    /** Returns the platform version carried by this. */
    public Optional<Version> platform() { return platform; }

    /** Returns the application version carried by this. */
    public Optional<ApplicationVersion> application() { return application; }

    public boolean isPinning() { return pinning; }

    /** Returns an instance representing no change */
    public static Change empty() { return empty; }

    /** Returns a version of this change which replaces or adds this platform change */
    public Change with(Version platformVersion) {
        if (pinning)
            throw new IllegalArgumentException("Not allowed to set a platform version when pinned.");

        return new Change(Optional.of(platformVersion), application, pinning);
    }

    /** Returns a version of this change which replaces or adds this application change */
    public Change with(ApplicationVersion applicationVersion) {
        return new Change(platform, Optional.of(applicationVersion), pinning);
    }

    /** Returns a change with the versions of this, and with the platform version pinned. */
    public Change withPin() {
        return new Change(platform, application, true);
    }

    /** Returns a change with the versions of this, and with the platform version unpinned. */
    public Change withoutPin() {
        return new Change(platform, application, false);
    }

    /** Returns the change obtained when overwriting elements of the given change with any present in this */
    public Change onTopOf(Change other) {
        if (platform.isPresent())
            other = other.with(platform.get());
        if (application.isPresent())
            other = other.with(application.get());
        if (pinning)
            other = other.withPin();
        return other;
    }

    @Override
    public int hashCode() { return Objects.hash(platform, application); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Change)) return false;
        Change o = (Change)other;
        if ( ! o.platform.equals(this.platform)) return false;
        if ( ! o.application.equals(this.application)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringJoiner changes = new StringJoiner(" and ");
        platform.ifPresent(version -> changes.add("upgrade to " + version.toString()));
        application.ifPresent(version -> changes.add("application change to " + version.id()));
        changes.setEmptyValue("no change");
        return changes.toString();
    }

    public static Change of(ApplicationVersion applicationVersion) {
        return new Change(Optional.empty(), Optional.of(applicationVersion), false);
    }

    public static Change of(Version platformChange) {
        return new Change(Optional.of(platformChange), Optional.empty(), false);
    }

    /** Returns whether this change carries an application downgrade relative to the given version. */
    public boolean downgrades(ApplicationVersion version) {
        return application.map(version::compareTo).orElse(0) > 0;
    }

    /** Returns whether this change carries a platform downgrade relative to the given version. */
    public boolean downgrades(Version version) {
        return platform.map(version::compareTo).orElse(0) > 0;
    }

    /** Returns whether this change carries an application upgrade relative to the given version. */
    public boolean upgrades(ApplicationVersion version) {
        return application.map(version::compareTo).orElse(0) < 0;
    }

    /** Returns whether this change carries a platform upgrade relative to the given version. */
    public boolean upgrades(Version version) {
        return platform.map(version::compareTo).orElse(0) < 0;
    }

}

