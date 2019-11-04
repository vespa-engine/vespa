// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An application package version, identified by a source revision and a build number.
 *
 * @author bratseth
 * @author mpolden
 */
public class ApplicationVersion implements Comparable<ApplicationVersion> {

    /**
     * Used in cases where application version cannot be determined, such as manual deployments (e.g. in dev
     * environment)
     */
    public static final ApplicationVersion unknown = new ApplicationVersion(Optional.empty(), OptionalLong.empty(),
                                                                            Optional.empty(), Optional.empty(), Optional.empty());

    // This never changes and is only used to create a valid semantic version number, as required by application bundles
    private static final String majorVersion = "1.0";

    private final Optional<SourceRevision> source;
    private final Optional<String> authorEmail;
    private final OptionalLong buildNumber;
    private final Optional<Version> compileVersion;
    private final Optional<Instant> buildTime;

    private ApplicationVersion(Optional<SourceRevision> source, OptionalLong buildNumber, Optional<String> authorEmail,
                               Optional<Version> compileVersion, Optional<Instant> buildTime) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(buildNumber, "buildNumber cannot be null");
        Objects.requireNonNull(authorEmail, "author cannot be null");
        if (source.isPresent() != buildNumber.isPresent()) {
            throw new IllegalArgumentException("both buildNumber and source must be set together");
        }
        if (compileVersion.isPresent() != buildTime.isPresent()) {
            throw new IllegalArgumentException("both compileVersion and buildTime must be set together");
        }
        if (buildNumber.isPresent() && buildNumber.getAsLong() <= 0) {
            throw new IllegalArgumentException("buildNumber must be > 0");
        }
        if (authorEmail.isPresent() && ! authorEmail.get().matches("[^@]+@[^@]+")) {
            throw new IllegalArgumentException("Invalid author email '" + authorEmail.get() + "'.");
        }
        if (compileVersion.isPresent() && compileVersion.get().equals(Version.emptyVersion)) {
            throw new IllegalArgumentException("The empty version is not a legal compile version.");
        }
        this.source = source;
        this.buildNumber = buildNumber;
        this.authorEmail = authorEmail;
        this.compileVersion = compileVersion;
        this.buildTime = buildTime;
    }

    /** Create an application package version from a completed build, without an author email */
    public static ApplicationVersion from(SourceRevision source, long buildNumber) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber), Optional.empty(),
                                      Optional.empty(), Optional.empty());
    }

    /** Creates an version from a completed build and an author email. */
    public static ApplicationVersion from(SourceRevision source, long buildNumber, String authorEmail) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber),
                                      Optional.of(authorEmail), Optional.empty(), Optional.empty());
    }

    /** Creates an version from a completed build, an author email, and build meta data. */
    public static ApplicationVersion from(SourceRevision source, long buildNumber, String authorEmail,
                                          Version compileVersion, Instant buildTime) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber), Optional.of(authorEmail),
                                      Optional.of(compileVersion), Optional.of(buildTime));
    }

    /** Returns an unique identifier for this version or "unknown" if version is not known */
    public String id() {
        if (isUnknown()) {
            return "unknown";
        }
        return String.format("%s.%d-%s", majorVersion, buildNumber.getAsLong(), abbreviateCommit(source.get().commit()));
    }

    /**
     * Returns information about the source of this revision, or empty if the source is not know/defined
     * (which is the case for command-line deployment from developers, but never for deployment jobs)
     */
    public Optional<SourceRevision> source() { return source; }

    /** Returns the build number that built this version */
    public OptionalLong buildNumber() { return buildNumber; }

    /** Returns the email of the author of commit of this version, if known */
    public Optional<String> authorEmail() { return authorEmail; }

    /** Returns the Vespa version this package was compiled against, if known. */
    public Optional<Version> compileVersion() { return compileVersion; }

    /** Returns the time this package was built, if known. */
    public Optional<Instant> buildTime() { return buildTime; }

    /** Returns whether this is unknown */
    public boolean isUnknown() {
        return this.equals(unknown);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationVersion)) return false;
        ApplicationVersion that = (ApplicationVersion) o;
        return Objects.equals(source, that.source) &&
               Objects.equals(buildNumber, that.buildNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, buildNumber);
    }

    @Override
    public String toString() {
        return   "Application package version: " + id()
               + source.map(s -> ", " + s.toString()).orElse("")
               + authorEmail.map(e -> ", by " + e).orElse("")
               + compileVersion.map(v -> ", built against " + v).orElse("")
               + buildTime.map(t -> " at " + t).orElse("") ;
    }

    /** Abbreviate given commit hash to 9 characters */
    private static String abbreviateCommit(String hash) {
        return hash.length() <= 9 ? hash : hash.substring(0, 9);
    }

    @Override
    public int compareTo(ApplicationVersion o) {
        if ( ! buildNumber().isPresent() || ! o.buildNumber().isPresent())
            return Boolean.compare(buildNumber().isPresent(), o.buildNumber.isPresent()); // Application package hash sorts first

        return Long.compare(buildNumber().getAsLong(), o.buildNumber().getAsLong());
    }
}
