// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;

import java.time.Instant;
import java.util.Objects;

/**
 * A change to an application
 *
 * @author bratseth
 */
public abstract class Change {

    private static NoChange none = new NoChange();

    /** Returns true if this change is blocked by the given spec at the given instant */
    public abstract boolean blockedBy(DeploymentSpec deploymentSpec, Instant instant);

    public abstract boolean isPresent();

    public static Change empty() { return none; }

    public static class NoChange extends Change {

        private NoChange() { }

        @Override
        public boolean isPresent() { return false; }

        @Override
        public boolean blockedBy(DeploymentSpec deploymentSpec, Instant instant) {
            return false;
        }

    }

    /** A change to the application package version of an application */
    public static class ApplicationChange extends Change {

        private final ApplicationVersion version;

        private ApplicationChange(ApplicationVersion version) {
            Objects.requireNonNull(version, "version cannot be null");
            this.version = version;
        }

        @Override
        public boolean isPresent() { return true; }

        /** The application package version in this change, or empty if not known yet */
        public ApplicationVersion version() { return version; }

        @Override
        public boolean blockedBy(DeploymentSpec deploymentSpec, Instant instant) {
            return ! deploymentSpec.canChangeRevisionAt(instant);
        }

        @Override
        public int hashCode() { return version.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if ( ! (other instanceof ApplicationChange)) return false;
            return ((ApplicationChange)other).version.equals(this.version);
        }

        /**
         * Creates an application change which we don't know anything about.
         * We are notified that a change has occurred by completion of the component job
         * but do not get to know about what the change is until a subsequent deployment
         * happens.
         */
        public static ApplicationChange unknown() {
            return new ApplicationChange(ApplicationVersion.unknown);
        }

        public static ApplicationChange of(ApplicationVersion version) {
            return new ApplicationChange(version);
        }

        @Override
        public String toString() {
            return "application change to " + version;
        }

    }

    /** A change to the Vespa version running an application */
    public static class VersionChange extends Change {

        private final Version version;

        public VersionChange(Version version) {
            Objects.requireNonNull(version, "version cannot be null");
            this.version = version;
        }

        @Override
        public boolean isPresent() { return true; }

        /** The Vespa version this changes to */
        public Version version() { return version; }

        @Override
        public boolean blockedBy(DeploymentSpec deploymentSpec, Instant instant) {
            return ! deploymentSpec.canUpgradeAt(instant);
        }

        @Override
        public int hashCode() { return version.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if ( ! (other instanceof VersionChange)) return false;
            return ((VersionChange)other).version.equals(this.version);
        }

        @Override
        public String toString() {
            return "version change to " + version;
        }

    }

}
