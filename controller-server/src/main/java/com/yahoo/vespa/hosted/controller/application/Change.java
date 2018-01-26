// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A change to an application
 * 
 * @author bratseth
 */
public abstract class Change {

    /** Returns true if this change is blocked by the given spec at the given instant */
    public abstract boolean blockedBy(DeploymentSpec deploymentSpec, Instant instant);
    
    /** A change to the application package version of an application */
    public static class ApplicationChange extends Change {

        // TODO: Make non-optional
        private final Optional<ApplicationVersion> version;
        
        private ApplicationChange(Optional<ApplicationVersion> version) {
            Objects.requireNonNull(version, "version cannot be null");
            this.version = version;
        }
        
        /** The application package version in this change, or empty if not known yet */
        public Optional<ApplicationVersion> version() { return version; }

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
            return new ApplicationChange(Optional.empty());
        }
        
        public static ApplicationChange of(ApplicationVersion version) {
            return new ApplicationChange(Optional.of(version));
        }

        @Override
        public String toString() { 
            return "application change to " + version.map(ApplicationVersion::toString).orElse("an unknown version");
        }
        
    }

    /** A change to the Vespa version running an application */
    public static class VersionChange extends Change {

        private final Version version;

        public VersionChange(Version version) {
            Objects.requireNonNull(version, "version cannot be null");
            this.version = version;
        }

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
