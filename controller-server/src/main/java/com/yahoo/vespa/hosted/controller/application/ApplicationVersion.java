// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.util.Objects;
import java.util.Optional;

/**
 * An application package version. This represents an build artifact, identified by a source revision and a build
 * number.
 *
 * @author bratseth
 * @author mpolden
 */
public class ApplicationVersion {

    // Never changes. Only used to create a valid version number for the bundle
    private static final String majorVersion = "1.0";

    // TODO: Remove after introducing new application version
    private final Optional<String> applicationPackageHash;

    private final Optional<SourceRevision> source;
    private final Optional<Long> buildNumber;

    private ApplicationVersion(Optional<String> applicationPackageHash, Optional<SourceRevision> source,
                               Optional<Long> buildNumber) {
        Objects.requireNonNull(applicationPackageHash, "applicationPackageHash cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(buildNumber, "buildNumber cannot be null");
        if (buildNumber.isPresent() && !source.isPresent()) {
            throw new IllegalArgumentException("both buildNumber and source must be set if buildNumber is set");
        }
        if (!buildNumber.isPresent() && !applicationPackageHash.isPresent()) {
            throw new IllegalArgumentException("applicationPackageHash must be given if buildNumber is unset");
        }
        this.applicationPackageHash = applicationPackageHash;
        this.source = source;
        this.buildNumber = buildNumber;
    }
    
    /** Create an application package revision where there is no information about its source */
    public static ApplicationVersion from(String applicationPackageHash) {
        return new ApplicationVersion(Optional.of(applicationPackageHash), Optional.empty(), Optional.empty());
    }

    /** Create an application package revision with a source */
    public static ApplicationVersion from(String applicationPackageHash, SourceRevision source) {
        return new ApplicationVersion(Optional.of(applicationPackageHash), Optional.of(source), Optional.empty());
    }

    /** Create an application package version from a completed build */
    public static ApplicationVersion from(SourceRevision source, long buildNumber) {
        return new ApplicationVersion(Optional.empty(), Optional.of(source), Optional.of(buildNumber));
    }

    /** Returns an unique identifier for this version */
    public String id() {
        if (applicationPackageHash.isPresent()) {
            return applicationPackageHash.get();
        }
        return String.format("%s.%d-%s", majorVersion, buildNumber.get(), abbreviateCommit(source.get().commit()));
    }

    /** 
     * Returns information about the source of this revision, or empty if the source is not know/defined
     * (which is the case for command-line deployment from developers, but never for deployment jobs)
     */
    public Optional<SourceRevision> source() { return source; }

    /** Returns the build number that built this version */
    public Optional<Long> buildNumber() { return buildNumber; }
    
    @Override
    public int hashCode() { return applicationPackageHash.hashCode(); }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if ( ! (other instanceof ApplicationVersion)) return false;
        return this.applicationPackageHash.equals(((ApplicationVersion)other).applicationPackageHash);
    }
    
    @Override
    public String toString() {
        if (buildNumber.isPresent()) {
            return "Application package version: " + abbreviateCommit(source.get().commit()) + "-" + buildNumber.get();
        }
        return "Application package revision '" + applicationPackageHash + "'" +
               (source.isPresent() ? " with " + source.get() : "");
    }

    /** Abbreviate given commit hash to 9 characters */
    private static String abbreviateCommit(String hash) {
        return hash.length() <= 9 ? hash : hash.substring(0, 9);
    }

}
