// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.container;

import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A container image.
 *
 * @author mpolden
 */
public class ContainerImage {

    private final String id;
    private final String registry;
    private final String repository;
    private final Instant createdAt;
    private final Version version;
    private final Optional<Architecture> architecture;

    public ContainerImage(String id, String registry, String repository, Instant createdAt, Version version, Optional<Architecture> architecture) {
        this.id = Objects.requireNonNull(id);
        this.registry = Objects.requireNonNull(registry);
        this.repository = Objects.requireNonNull(repository);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = Objects.requireNonNull(version);
        this.architecture = Objects.requireNonNull(architecture);
    }

    /** Unique identifier of this */
    public String id() {
        return id;
    }

    /** The registry holding this image */
    public String registry() {
        return registry;
    }

    /** Repository of this image */
    public String repository() {
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
        ContainerImage that = (ContainerImage) o;
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
        return "container image " + repository + " [registry=" + registry + ",version=" + version.toFullString() +
               ",createdAt=" + createdAt + ",architecture=" + architecture.map(Enum::name).orElse("<none>") + "]";
    }

    public enum Architecture {
        amd64,
        arm64,
    }

}
