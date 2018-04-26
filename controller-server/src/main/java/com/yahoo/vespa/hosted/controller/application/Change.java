// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;

import java.time.Instant;
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

    private static final Change empty = new Change(Optional.empty(), Optional.empty());

    /** The platform version we are upgrading to, or empty if none */
    private final Optional<Version> platform;

    /** The application version we are changing to, or empty if none */
    private final Optional<ApplicationVersion> application;

    private Change(Optional<Version> platform, Optional<ApplicationVersion> application) {
        Objects.requireNonNull(platform, "platform cannot be null");
        Objects.requireNonNull(application, "application cannot be null");
        if (application.isPresent() && application.get().isUnknown()) {
            throw new IllegalArgumentException("Application version to deploy must be a known version");
        }
        this.platform = platform;
        this.application = application;
    }

    public Change withoutPlatform() {
        return new Change(Optional.empty(), application);
    }

    public Change withoutApplication() {
        return new Change(platform, Optional.empty());
    }

    /** Returns whether a change should currently be deployed */
    public boolean isPresent() {
        return platform.isPresent() || application.isPresent();
    }

    /** Returns the platform version carried by this. */
    public Optional<Version> platform() { return platform; }

    /** Returns the application version carried by this. */
    public Optional<ApplicationVersion> application() { return application; }

    /** Returns an instance representing no change */
    public static Change empty() { return empty; }

    /** Returns a version of this change which replaces or adds this application change */
    public Change with(ApplicationVersion applicationVersion) {
        return new Change(platform, Optional.of(applicationVersion));
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
        return new Change(Optional.empty(), Optional.of(applicationVersion));
    }

    public static Change of(Version platformChange) {
        return new Change(Optional.of(platformChange), Optional.empty());
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

