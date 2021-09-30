// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.container;

import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;

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

    public ContainerImage(String id, String registry, String repository, Instant createdAt, Version version) {
        this.id = Objects.requireNonNull(id);
        this.registry = Objects.requireNonNull(registry);
        this.repository = Objects.requireNonNull(repository);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = Objects.requireNonNull(version);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerImage that = (ContainerImage) o;
        return id.equals(that.id) &&
               registry.equals(that.registry) &&
               repository.equals(that.repository) &&
               createdAt.equals(that.createdAt) &&
               version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, registry, repository, createdAt, version);
    }

    @Override
    public String toString() {
        return "container image " + repository + " [registry=" + registry + ",version=" + version.toFullString() +
               ",createdAt=" + createdAt + "]";
    }

}
