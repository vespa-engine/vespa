// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import ai.vespa.validation.Validation;
import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import static ai.vespa.validation.Validation.requireAtLeast;

/**
 * An application package version, identified by a source revision and a build number.
 *
 * @author bratseth
 * @author mpolden
 * @author jonmv
 */
public class ApplicationVersion implements Comparable<ApplicationVersion> {

    /** Should not be used, but may still exist in serialized data :S */
    public static final ApplicationVersion unknown = new ApplicationVersion(Optional.empty(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true, Optional.empty(), false, true, Optional.empty(), 0);

    // This never changes and is only used to create a valid semantic version number, as required by application bundles
    private static final String majorVersion = "1.0";

    private final Optional<SourceRevision> source;
    private final Optional<String> authorEmail;
    private final OptionalLong buildNumber;
    private final Optional<Version> compileVersion;
    private final Optional<Instant> buildTime;
    private final Optional<String> sourceUrl;
    private final Optional<String> commit;
    private final boolean deployedDirectly;
    private final Optional<String> bundleHash;
    private final boolean hasPackage;
    private final boolean shouldSkip;
    private final Optional<String> description;
    private final int risk;

    public ApplicationVersion(Optional<SourceRevision> source, OptionalLong buildNumber, Optional<String> authorEmail,
                              Optional<Version> compileVersion, Optional<Instant> buildTime, Optional<String> sourceUrl,
                              Optional<String> commit, boolean deployedDirectly, Optional<String> bundleHash,
                              boolean hasPackage, boolean shouldSkip, Optional<String> description, int risk) {
        if (buildNumber.isEmpty() && (   source.isPresent() || authorEmail.isPresent() || compileVersion.isPresent()
                                      || buildTime.isPresent() || sourceUrl.isPresent() || commit.isPresent()))
            throw new IllegalArgumentException("Build number must be present if any other attribute is");

        if (buildNumber.isPresent() && buildNumber.getAsLong() <= 0)
            throw new IllegalArgumentException("Build number must be > 0");

        if (commit.isPresent() && commit.get().length() > 128)
            throw new IllegalArgumentException("Commit may not be longer than 128 characters");

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
        this.deployedDirectly = deployedDirectly;
        this.bundleHash = bundleHash;
        this.hasPackage = hasPackage;
        this.shouldSkip = shouldSkip;
        this.description = description;
        this.risk = requireAtLeast(risk, "application build risk", 0);
    }

    public RevisionId id() {
        return isDeployedDirectly() ? RevisionId.forDevelopment(buildNumber().orElse(0))
                                    : RevisionId.forProduction(buildNumber().orElseThrow());
    }

    /** Create an application package version from a completed build, without an author email */
    public static ApplicationVersion from(SourceRevision source, long buildNumber) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty(), true, false, Optional.empty(), 0);
    }

    /** Creates a version from a completed build, an author email, and build metadata. */
    public static ApplicationVersion from(SourceRevision source, long buildNumber, String authorEmail,
                                          Version compileVersion, Instant buildTime) {
        return new ApplicationVersion(Optional.of(source), OptionalLong.of(buildNumber), Optional.of(authorEmail), Optional.of(compileVersion), Optional.of(buildTime), Optional.empty(), Optional.empty(), false, Optional.empty(), true, false, Optional.empty(), 0);
    }

    /** Creates a minimal version for a development build. */
    public static ApplicationVersion forDevelopment(long buildNumber, Optional<Version> compileVersion) {
        return new ApplicationVersion(Optional.empty(), OptionalLong.of(buildNumber), Optional.empty(), compileVersion, Optional.empty(), Optional.empty(), Optional.empty(), true, Optional.empty(), true, false, Optional.empty(), 0);
    }

    /** Creates a version from a completed build, an author email, and build metadata. */
    public static ApplicationVersion forProduction(Optional<SourceRevision> source, long buildNumber, Optional<String> authorEmail,
                                                   Optional<Version> compileVersion, Optional<Instant> buildTime,
                                                   Optional<String> sourceUrl, Optional<String> commit, boolean deployedDirectly,
                                                   Optional<String> bundleHash, Optional<String> description, int risk) {
        return new ApplicationVersion(source, OptionalLong.of(buildNumber), authorEmail, compileVersion, buildTime,
                                      sourceUrl, commit, deployedDirectly, bundleHash, true, false, description, risk);
    }

    /** Returns a unique identifier for this version or "unknown" if version is not known */
    // TODO jonmv: kill
    public String stringId() {
        if (isUnknown()) return "unknown";

        return source.map(SourceRevision::commit).map(ApplicationVersion::abbreviateCommit)
                .or(this::commit)
                .map(commit -> String.format("%s.%d-%s", majorVersion, buildNumber.getAsLong(), commit))
                .orElseGet(() -> majorVersion + "." + buildNumber.getAsLong());
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

    /** Returns whether this is unknown */
    public boolean isUnknown() {
        return this.equals(unknown);
    }

    /** Returns whether the application package for this version was deployed directly to zone */
    public boolean isDeployedDirectly() {
        return deployedDirectly;
    }

    /** Returns a copy of this without a package stored. */
    public ApplicationVersion withoutPackage() {
        return new ApplicationVersion(source, buildNumber, authorEmail, compileVersion, buildTime, sourceUrl, commit, deployedDirectly, bundleHash, false, shouldSkip, description, risk);
    }

    /** Whether we still have the package for this revision. */
    public boolean hasPackage() {
        return hasPackage;
    }

    /** Returns a copy of this which will not be rolled out to production. */
    public ApplicationVersion skipped() {
        return new ApplicationVersion(source, buildNumber, authorEmail, compileVersion, buildTime, sourceUrl, commit, deployedDirectly, bundleHash, hasPackage, true, description, risk);
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
        return    Objects.equals(buildNumber, that.buildNumber)
               && Objects.equals(commit(), that.commit())
               && deployedDirectly == that.deployedDirectly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildNumber, commit(), deployedDirectly);
    }

    @Override
    public String toString() {
        return "Application package version: " + stringId()
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

        if (deployedDirectly != o.deployedDirectly)
            return Boolean.compare( ! deployedDirectly, ! o.deployedDirectly); // Directly deployed versions sort first

        return Long.compare(buildNumber().getAsLong(), o.buildNumber().getAsLong());
    }

}
