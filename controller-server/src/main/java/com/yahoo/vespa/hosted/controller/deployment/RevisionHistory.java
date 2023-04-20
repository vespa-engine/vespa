package com.yahoo.vespa.hosted.controller.deployment;

import ai.vespa.validation.Validation;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

import static ai.vespa.validation.Validation.require;
import static java.util.Collections.emptyNavigableMap;

/**
 * History of application revisions for an {@link com.yahoo.vespa.hosted.controller.Application}.
 *
 * @author jonmv
 */
public class RevisionHistory {

    private static final Comparator<JobId> comparator = Comparator.comparing(JobId::application).thenComparing(JobId::type);

    private final NavigableMap<RevisionId, ApplicationVersion> production;
    private final NavigableMap<JobId, NavigableMap<RevisionId, ApplicationVersion>> development;

    private RevisionHistory(NavigableMap<RevisionId, ApplicationVersion> production,
                           NavigableMap<JobId, NavigableMap<RevisionId, ApplicationVersion>> development) {
        this.production = production;
        this.development = development;
    }

    public static RevisionHistory empty() {
        return ofRevisions(List.of(), Map.of());
    }

    public static RevisionHistory ofRevisions(Collection<ApplicationVersion> productionRevisions,
                                              Map<JobId, ? extends Collection<ApplicationVersion>> developmentRevisions) {
        NavigableMap<RevisionId, ApplicationVersion> production = new TreeMap<>();
        for (ApplicationVersion revision : productionRevisions)
            production.put(revision.id(), revision);

        NavigableMap<JobId, NavigableMap<RevisionId, ApplicationVersion>> development = new TreeMap<>(comparator);
        developmentRevisions.forEach((job, jobRevisions) -> {
            NavigableMap<RevisionId, ApplicationVersion> revisions = development.computeIfAbsent(job, __ -> new TreeMap<>());
            for (ApplicationVersion revision : jobRevisions)
                revisions.put(revision.id(), revision);
        });

        return new RevisionHistory(production, development);
    }

    /** Returns a copy of this without any production revisions older than the given. */
    public RevisionHistory withoutOlderThan(RevisionId id) {
        if (production.headMap(id).isEmpty()) return this;
        return new RevisionHistory(production.tailMap(id, true), development);
    }

    /** Returns a copy of this without any development revisions older than the given. */
    public RevisionHistory withoutOlderThan(RevisionId id, JobId job) {
        if ( ! development.containsKey(job) || development.get(job).headMap(id).isEmpty()) return this;
        NavigableMap<JobId, NavigableMap<RevisionId, ApplicationVersion>> development = new TreeMap<>(this.development);
        development.compute(job, (__, revisions) -> revisions.tailMap(id, true));
        return new RevisionHistory(production, development);
    }

    /** Returns a copy of this with the revision added or updated. */
    public RevisionHistory with(ApplicationVersion revision) {
        if (revision.id().isProduction()) {
            if ( ! production.isEmpty() && revision.bundleHash().flatMap(hash -> production.lastEntry().getValue().bundleHash().map(hash::equals)).orElse(false))
                revision = revision.skipped();

            NavigableMap<RevisionId, ApplicationVersion> production = new TreeMap<>(this.production);
            production.put(revision.id(), revision);
            return new RevisionHistory(production, development);
        }
        else {
            NavigableMap<JobId, NavigableMap<RevisionId, ApplicationVersion>> development = new TreeMap<>(this.development);
            NavigableMap<RevisionId, ApplicationVersion> revisions = development.compute(revision.id().job(), (__, old) -> new TreeMap<>(old != null ? old : emptyNavigableMap()));
            if ( ! revisions.isEmpty()) revisions.compute(revisions.lastKey(), (__, last) -> last.withoutPackage());
            revisions.put(revision.id(), revision);
            return new RevisionHistory(production, development);
        }
    }

    // Fallback for when an application version isn't known for the given key.
    private static ApplicationVersion revisionOf(RevisionId id) {
        return new ApplicationVersion(id, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, false, Optional.empty(), 0);
    }

    /** Returns the production {@link ApplicationVersion} with this revision ID. */
    public ApplicationVersion get(RevisionId id) {
        return id.isProduction() ? production.getOrDefault(id, revisionOf(id))
                                 : development.getOrDefault(id.job(), emptyNavigableMap())
                                              .getOrDefault(id, revisionOf(id));
    }

    /** Returns the last submitted production build. */
    public Optional<ApplicationVersion> last() {
        return Optional.ofNullable(production.lastEntry()).map(Map.Entry::getValue);
    }

    /** Returns all known production revisions we still have the package for, from oldest to newest. */
    public List<ApplicationVersion> withPackage() {
        return production.values().stream()
                         .filter(ApplicationVersion::hasPackage)
                         .toList();
    }

    /** Returns the currently deployable revisions of the application. */
    public Deque<ApplicationVersion> deployable(boolean ascending) {
        Deque<ApplicationVersion> versions = new ArrayDeque<>();
        for (ApplicationVersion version : withPackage()) {
            if (version.isDeployable()) {
                if (ascending) versions.addLast(version);
                else versions.addFirst(version);
            }
        }
        return versions;
    }

    /** All known production revisions, in ascending order. */
    public List<ApplicationVersion> production() {
        return List.copyOf(production.values());
    }

    /* All known development revisions, in ascending order, per job. */
    public NavigableMap<JobId, List<ApplicationVersion>> development() {
        NavigableMap<JobId, List<ApplicationVersion>> copy = new TreeMap<>(comparator);
        development.forEach((job, revisions) -> copy.put(job, List.copyOf(revisions.values())));
        return Collections.unmodifiableNavigableMap(copy);
    }

}
