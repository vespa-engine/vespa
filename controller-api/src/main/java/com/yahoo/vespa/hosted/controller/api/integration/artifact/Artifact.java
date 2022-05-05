// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.artifact;

import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A registry artifact (e.g. container image or RPM)
 *
 * @author mpolden
 */
public class Artifact {

    private final String id;
    private final Optional<String> registry;
    private final Optional<String> repository;
    private final Optional<String> tag;
    private final Instant createdAt;
    private final Version version;

    public Artifact(String id, String registry, String repository, String tag, Instant createdAt, Version version) {
        this.id = Objects.requireNonNull(id);
        this.registry = Optional.of(registry);
        this.repository = Optional.of(repository);
        this.tag = Optional.of(tag);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = Objects.requireNonNull(version);
    }

    public Artifact(String id, Instant createdAt, Optional<String> tag, Version version) {
        this.id = Objects.requireNonNull(id);
        this.registry = Optional.empty();
        this.repository = Optional.empty();
        this.tag = Objects.requireNonNull(tag);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = Objects.requireNonNull(version);
    }

    /** Unique identifier of this */
    public String id() {
        return id;
    }

    /** The registry holding this artifact */
    public Optional<String> registry() {
        return registry;
    }

    /** Repository of this artifact */
    public Optional<String> repository() {
        return repository;
    }

    /** Tag of this artifact */
    public Optional<String> tag() {
        return tag;
    }

    /** The time this was created */
    public Instant createdAt() {
        return createdAt;
    }

    /** The version of this */
    public Version version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact that = (Artifact) o;
        return id.equals(that.id) &&
               registry.equals(that.registry) &&
               repository.equals(that.repository) &&
               tag.equals(that.tag) &&
               createdAt.equals(that.createdAt) &&
               version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, registry, repository, tag, createdAt, version);
    }

    @Override
    public String toString() {
        String name = repository.isPresent() ? registry.get() + "/" + repository.get() : id;
        return "artifact " + name + " [version=" + version.toFullString() + ",createdAt=" + createdAt + tag.map(t -> ",tag=" + t).orElse("") + "]";
    }
}
