// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import ai.vespa.validation.Validation;
import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import static ai.vespa.validation.Validation.requireAtLeast;
import static java.util.Objects.requireNonNull;

/**
 * An application package version, identified by a source revision and a build number.
 *
 * @author bratseth
 * @author mpolden
 * @author jonmv
 */
public class ApplicationVersion implements Comparable<ApplicationVersion> {

    // This never changes and is only used to create a valid semantic version number, as required by application bundles
    private static final String majorVersion = "1.0";

    private final RevisionId id;
    private final Optional<SourceRevision> source;
    private final Optional<String> authorEmail;
    private final Optional<Version> compileVersion;
    private final Optional<Integer> allowedMajor;
    private final Optional<Instant> buildTime;
    private final Optional<String> sourceUrl;
    private final Optional<String> commit;
    private final Optional<String> bundleHash;
    private final boolean hasPackage;
    private final boolean shouldSkip;
    private final Optional<String> description;
    private final int risk;

    public ApplicationVersion(RevisionId id, Optional<SourceRevision> source, Optional<String> authorEmail,
                              Optional<Version> compileVersion, Optional<Integer> allowedMajor, Optional<Instant> buildTime,
                              Optional<String> sourceUrl, Optional<String> commit, Optional<String> bundleHash,
                              boolean hasPackage, boolean shouldSkip, Optional<String> description, int risk) {

        if (commit.isPresent() && commit.get().length() > 128)
            throw new IllegalArgumentException("Commit may not be longer than 128 characters");

        if (authorEmail.isPresent() && ! authorEmail.get().matches("[^@]+@[^@]+"))
            throw new IllegalArgumentException("Invalid author email '" + authorEmail.get() + "'.");

        if (compileVersion.isPresent() && compileVersion.get().equals(Version.emptyVersion))
            throw new IllegalArgumentException("The empty version is not a legal compile version.");

        this.id = id;
        this.source = source;
        this.authorEmail = authorEmail;
        this.compileVersion = compileVersion;
        this.allowedMajor = requireNonNull(allowedMajor);
        this.buildTime = buildTime;
        this.sourceUrl = requireNonNull(sourceUrl, "sourceUrl cannot be null");
        this.commit = requireNonNull(commit, "commit cannot be null");
        this.bundleHash = bundleHash;
        this.hasPackage = hasPackage;
        this.shouldSkip = shouldSkip;
        this.description = description;
        this.risk = requireAtLeast(risk, "application build risk", 0);
    }

    public RevisionId id() {
        return id;
    }

    /** Create an application package version from a completed build, without an author email */
    public static ApplicationVersion from(RevisionId id, SourceRevision source) {
        return new ApplicationVersion(id, Optional.of(source), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true, false, Optional.empty(), 0);
    }

    /** Creates a version from a completed build, an author email, and build metadata. */
    public static ApplicationVersion from(RevisionId id, SourceRevision source, String authorEmail, Version compileVersion, Instant buildTime) {
        return new ApplicationVersion(id, Optional.of(source), Optional.of(authorEmail), Optional.of(compileVersion), Optional.empty(), Optional.of(buildTime), Optional.empty(), Optional.empty(), Optional.empty(), true, false, Optional.empty(), 0);
    }

    /** Creates a minimal version for a development build. */
    public static ApplicationVersion forDevelopment(RevisionId id, Optional<Version> compileVersion, Optional<Integer> allowedMajor) {
        return new ApplicationVersion(id, Optional.empty(), Optional.empty(), compileVersion, allowedMajor, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true, false, Optional.empty(), 0);
    }

    /** Creates a version from a completed build, an author email, and build metadata. */
    public static ApplicationVersion forProduction(RevisionId id, Optional<SourceRevision> source, Optional<String> authorEmail,
                                                   Optional<Version> compileVersion, Optional<Integer> allowedMajor, Optional<Instant> buildTime, Optional<String> sourceUrl,
                                                   Optional<String> commit, Optional<String> bundleHash, Optional<String> description, int risk) {
        return new ApplicationVersion(id, source, authorEmail, compileVersion, allowedMajor, buildTime,
                                      sourceUrl, commit, bundleHash, true, false, description, risk);
    }

    /** Returns a unique identifier for this version or "unknown" if version is not known */
    // TODO jonmv: kill
    public String stringId() {
        return source.map(SourceRevision::commit).map(ApplicationVersion::abbreviateCommit)
                .or(this::commit)
                .map(commit -> String.format("%s.%d-%s", majorVersion, buildNumber().getAsLong(), commit))
                .orElseGet(() -> majorVersion + "." + buildNumber().getAsLong());
    }

    /**
     * Returns information about the source of this revision, or empty if the source is not know/defined
     * (which is the case for command-line deployment from developers, but never for deployment jobs)
     */
    public Optional<SourceRevision> source() { return source; }

    /** Returns the build number that built this version */
    public OptionalLong buildNumber() { return OptionalLong.of(id.number()); }

    /** Returns the email of the author of commit of this version, if known */
    public Optional<String> authorEmail() { return authorEmail; }

    /** Returns the Vespa version this package was compiled against, if known. */
    public Optional<Version> compileVersion() { return compileVersion; }

    public Optional<Integer> allowedMajor() { return allowedMajor; }

    /** Returns the time this package was built, if known. */
    public Optional<Instant> buildTime() { return buildTime; }

    /** Returns the hash of app package except deployment/build-meta data */
    public Optional<String> bundleHash() {
        return bundleHash;
    }

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

    /** Returns whether the application package for this version was deployed directly to zone */
    public boolean isDeployedDirectly() {
        return ! id.isProduction();
    }

    /** Returns a copy of this without a package stored. */
    public ApplicationVersion withoutPackage() {
        return new ApplicationVersion(id, source, authorEmail, compileVersion, allowedMajor, buildTime, sourceUrl, commit, bundleHash, false, shouldSkip, description, risk);
    }

    /** Whether we still have the package for this revision. */
    public boolean hasPackage() {
        return hasPackage;
    }

    /** Returns a copy of this which will not be rolled out to production. */
    public ApplicationVersion skipped() {
        return new ApplicationVersion(id, source, authorEmail, compileVersion, allowedMajor, buildTime, sourceUrl, commit, bundleHash, hasPackage, true, description, risk);
    }

    /** Whether we still have the package for this revision. */
    public boolean shouldSkip() {
        return shouldSkip;
    }

    /** Whether this revision should be deployed. */
    public boolean isDeployable() {
        return hasPackage && ! shouldSkip;
    }

    /** An optional, free-text description on this build. */
    public Optional<String> description() {
        return description;
    }

    /** The assumed risk of rolling out this revision, relative to the previous. */
    public int risk() {
        return risk;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof ApplicationVersion)) return false;
        ApplicationVersion that = (ApplicationVersion) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id +
               source.map(s -> ", " + s).orElse("") +
               authorEmail.map(e -> ", by " + e).orElse("") +
               compileVersion.map(v -> ", built against " + v).orElse("") +
               buildTime.map(t -> " at " + t).orElse("") ;
    }

    /** Abbreviate given commit hash to 9 characters */
    private static String abbreviateCommit(String hash) {
        return hash.length() <= 9 ? hash : hash.substring(0, 9);
    }

    @Override
    public int compareTo(ApplicationVersion o) {
        return id.compareTo(o.id);
    }

}
