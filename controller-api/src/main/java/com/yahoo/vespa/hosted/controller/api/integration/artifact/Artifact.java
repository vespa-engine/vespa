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
    private final Instant createdAt;
    private final Version version;
    private final Optional<Architecture> architecture;

    public Artifact(String id, String registry, String repository, Instant createdAt, Version version, Optional<Architecture> architecture) {
        this.id = Objects.requireNonNull(id);
        this.registry = Optional.of(registry);
        this.repository = Optional.of(repository);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = Objects.requireNonNull(version);
    }

    public Artifact(String id, Instant createdAt, Version version) {
        this.id = Objects.requireNonNull(id);
        this.registry = Optional.empty();
        this.repository = Optional.empty();
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = Objects.requireNonNull(version);
        this.architecture = Objects.requireNonNull(architecture);
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

    /** The time this was created */
    public Instant createdAt() {
        return createdAt;
    }

    /** The version of this */
    public Version version() {
        return version;
    }

    /** The architecture of this, if any */
    public Optional<Architecture> architecture() {
        return architecture;
    }

    /** The tag of this image */
    public String tag() {
        return version().toFullString() + architecture.map(arch -> "-" + arch.name()).orElse("");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact that = (Artifact) o;
        return id.equals(that.id) &&
               registry.equals(that.registry) &&
               repository.equals(that.repository) &&
               createdAt.equals(that.createdAt) &&
               version.equals(that.version) &&
               architecture.equals(that.architecture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, registry, repository, createdAt, version, architecture);
    }

    @Override
    public String toString() {
        String name = repository.isPresent() ? registry.get() + "/" + repository.get() : id;
        return "artifact " + name + " [version=" + version.toFullString() + ",createdAt=" + createdAt + ",architecture=" + architecture.map(Enum::name).orElse("<none>") + "]";
    }

    public enum Architecture {
        amd64,
        arm64,
    }

}
