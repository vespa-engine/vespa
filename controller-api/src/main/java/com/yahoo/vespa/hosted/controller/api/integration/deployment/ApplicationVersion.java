// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.component.Version;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An application package version, identified by a source revision and a build number.
 *
 * @author bratseth
 * @author mpolden
 * @author jonmv
 */
public class ApplicationVersion implements Comparable<ApplicationVersion> {

    /**
     * Used in cases where application version cannot be determined, such as manual deployments (e.g. in dev
     * environment)
     */
    public static final ApplicationVersion unknown = new ApplicationVersion(Optional.empty(), OptionalLong.empty(),
                                                                            Optional.empty(), Optional.empty(), Optional.empty(),
                                                                            Optional.empty(), Optional.empty());

    // This never changes and is only used to create a valid semantic version number, as required by application bundles
    private static final String majorVersion = "1.0";

    private final Optional<SourceRevision> source;
    private final Optional<String> authorEmail;
    private final OptionalLong buildNumber;
    private final Optional<Version> compileVersion;
    private final Optional<Instant> buildTime;
    private final Optional<String> sourceUrl;
    private final Optional<String> commit;

    /** Public for serialisation only. */
    public ApplicationVersion(Optional<SourceRevision> source, OptionalLong buildNumber, Optional<String> authorEmail,
                               Optional<Version> compileVersion, Optional<Instant> buildTime, Optional<String> sourceUrl,
                               Optional<String> commit) {
        if (buildNumber.isEmpty() && (   source.isPresent() || authorEmail.isPresent() || compileVersion.isPresent()
                                      || buildTime.isPresent() || sourceUrl.isPresent() || commit.isPresent()))
            throw new IllegalArgumentException("Build number must be present if any other attribute is");

        if (buildNumber.isPresent() && buildNumber.getAsLong() <= 0)
            throw new IllegalArgumentException("Build number must be > 0");

        if (commit.isPresent() && commit.get().length() > 128)
            throw new IllegalArgumentException("Commit may not be longer than 128 characters");

        sourceUrl.map(URI::create);

        if (authorEmail.isPresent() && ! authorEmail.get().matches("[^@]+@[^@]+"))
            throw new IllegalArgumentException("Invalid author email '" + authorEmail.get() + "'.");

        if (compileVersion.isPresent() && compileVersion.get().equals(Version.emptyVersion))
            throw new IllegalArgumentException("The empty version is not a legal compile version.");

        this.source = source;
        this.buildNumber = buildNumber;
        this.authorEmail = authorEmail;
        this.compileVersion = compileVersion;
        this.buildTime = buildTime;
        this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl cannot be null");
        this.commit = Objects.requireNonNull(commit, "commit cannot be null");
    }

    /** Create an application package version from a completed build, without an author email */
    public static ApplicationVersion from(SourceRevision source, long buildNumber) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber), Optional.empty(),
                                      Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Creates an version from a completed build and an author email. */
    public static ApplicationVersion from(SourceRevision source, long buildNumber, String authorEmail) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber), Optional.of(authorEmail),
                                      Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Creates an version from a completed build, an author email, and build meta data. */
    public static ApplicationVersion from(SourceRevision source, long buildNumber, String authorEmail,
                                          Version compileVersion, Instant buildTime) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber), Optional.of(authorEmail),
                                      Optional.of(compileVersion), Optional.of(buildTime), Optional.empty(), Optional.empty());
    }

    /** Creates an version from a completed build, an author email, and build meta data. */
    public static ApplicationVersion from(Optional<SourceRevision> source, long buildNumber, Optional<String> authorEmail,
                                          Optional<Version> compileVersion, Optional<Instant> buildTime,
                                          Optional<String> sourceUrl, Optional<String> commit) {
        return new ApplicationVersion(source, OptionalLong.of(buildNumber), authorEmail, compileVersion, buildTime, sourceUrl, commit);
    }

    /** Returns an unique identifier for this version or "unknown" if version is not known */
    public String id() {
        if (isUnknown()) {
            return "unknown";
        }
        return String.format("%s.%d-%s",
                             majorVersion,
                             buildNumber.getAsLong(),
                             source.map(SourceRevision::commit).map(commit -> abbreviateCommit(commit))
                                   .or(this::commit)
                                   .orElse("unknown"));
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

    /** Returns the source URL for this application version. */
    public Optional<String> sourceUrl() {
        return sourceUrl.or(() -> source.map(source -> {
            String repository = source.repository();
            if (repository.startsWith("git@"))
                repository = "https://" + repository.substring(4).replace(':', '/');
            if (repository.endsWith(".git"))
                repository = repository.substring(0, repository.length() - 4);
            return repository + "/tree/" + source.commit();
        }));
    }

    /** Returns the commit name of this application version. */
    public Optional<String> commit() { return commit.or(() -> source.map(SourceRevision::commit)); }

    /** Returns whether this is unknown */
    public boolean isUnknown() {
        return this.equals(unknown);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof ApplicationVersion)) return false;
        ApplicationVersion that = (ApplicationVersion) o;
        return    Objects.equals(buildNumber, that.buildNumber)
               && Objects.equals(commit(), that.commit());
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildNumber, commit());
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
        if (buildNumber().isEmpty() || o.buildNumber().isEmpty())
            return Boolean.compare(buildNumber().isPresent(), o.buildNumber.isPresent()); // Unknown version sorts first

        return Long.compare(buildNumber().getAsLong(), o.buildNumber().getAsLong());
    }

}
