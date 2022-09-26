// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Source and target versions for an application.
 *
 * @author jvenstad
 * @author mpolden
 */
public class Versions {

    private final Version targetPlatform;
    private final RevisionId targetRevision;
    private final Optional<Version> sourcePlatform;
    private final Optional<RevisionId> sourceRevision;

    public Versions(Version targetPlatform, RevisionId targetRevision, Optional<Version> sourcePlatform,
                    Optional<RevisionId> sourceRevision) {
        if (sourcePlatform.isPresent() ^ sourceRevision.isPresent())
            throw new IllegalArgumentException("Sources must both be present or absent.");

        this.targetPlatform = requireNonNull(targetPlatform);
        this.targetRevision = requireNonNull(targetRevision);
        this.sourcePlatform = requireNonNull(sourcePlatform);
        this.sourceRevision = requireNonNull(sourceRevision);
    }

    /** A copy of this, without source versions. */
    public Versions withoutSources() {
        return new Versions(targetPlatform, targetRevision, Optional.empty(), Optional.empty());
    }

    /** Target platform version for this */
    public Version targetPlatform() {
        return targetPlatform;
    }

    /** Target revision for this */
    public RevisionId targetRevision() {
        return targetRevision;
    }

    /** Source platform version for this */
    public Optional<Version> sourcePlatform() {
        return sourcePlatform;
    }

    /** Source application version for this */
    public Optional<RevisionId> sourceRevision() {
        return sourceRevision;
    }

    /** Returns whether source versions are present and match those of the given job other versions. */
    public boolean sourcesMatchIfPresent(Versions versions) {
        return (sourcePlatform.map(targetPlatform::equals).orElse(true) ||
                sourcePlatform.equals(versions.sourcePlatform())) &&
               (sourceRevision.map(targetRevision::equals).orElse(true) ||
                sourceRevision.equals(versions.sourceRevision()));
    }

    public boolean targetsMatch(Versions versions) {
        return targetPlatform.equals(versions.targetPlatform()) &&
               targetRevision.equals(versions.targetRevision());
    }

    /** Returns whether this change could result in the given target versions. */
    public boolean targetsMatch(Change change) {
        return    change.platform().map(targetPlatform::equals).orElse(true)
               && change.revision().map(targetRevision::equals).orElse(true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof Versions)) return false;
        Versions versions = (Versions) o;
        return Objects.equals(targetPlatform, versions.targetPlatform) &&
               Objects.equals(targetRevision, versions.targetRevision) &&
               Objects.equals(sourcePlatform, versions.sourcePlatform) &&
               Objects.equals(sourceRevision, versions.sourceRevision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetPlatform, targetRevision, sourcePlatform, sourceRevision);
    }

    @Override
    public String toString() {
        return Text.format("platform %s%s, revision %s%s",
                             sourcePlatform.filter(source -> ! source.equals(targetPlatform))
                                           .map(source -> source + " -> ").orElse(""),
                             targetPlatform,
                             sourceRevision.filter(source -> ! source.equals(targetRevision))
                                              .map(source -> source + " -> ").orElse(""),
                             targetRevision);
    }

    /** Create versions using given change and application */
    public static Versions from(Change change, Application application, Optional<Version> existingPlatform,
                                Optional<RevisionId> existingRevision, Supplier<Version> defaultPlatformVersion) {
        return new Versions(targetPlatform(application, change, existingPlatform, defaultPlatformVersion),
                            targetRevision(application, change, existingRevision),
                            existingPlatform,
                            existingRevision);
    }

    /** Create versions using given change and application */
    public static Versions from(Change change, Application application, Optional<Deployment> deployment, Supplier<Version> defaultPlatformVersion) {
        return from(change, application, deployment.map(Deployment::version), deployment.map(Deployment::revision), defaultPlatformVersion);
    }

    private static Version targetPlatform(Application application, Change change, Optional<Version> existing,
                                          Supplier<Version> defaultVersion) {
        if (change.isPinned() && change.platform().isPresent())
            return change.platform().get();

        return max(change.platform(), existing)
                .orElseGet(() -> application.oldestDeployedPlatform().orElseGet(defaultVersion));
    }

    private static RevisionId targetRevision(Application application, Change change,
                                             Optional<RevisionId> existing) {
        return change.revision()
                     .or(() -> existing)
                     .orElseGet(() -> defaultRevision(application));
    }

    private static RevisionId defaultRevision(Application application) {
        return application.oldestDeployedRevision()
                          .or(() -> application.revisions().last().map(ApplicationVersion::id))
                          .orElseThrow(() -> new IllegalStateException("no known prod revisions, but asked for one, for " + application));
    }

    private static <T extends Comparable<T>> Optional<T> max(Optional<T> o1, Optional<T> o2) {
        return o1.isEmpty() ? o2 : o2.isEmpty() ? o1 : o1.get().compareTo(o2.get()) >= 0 ? o1 : o2;
    }

}
