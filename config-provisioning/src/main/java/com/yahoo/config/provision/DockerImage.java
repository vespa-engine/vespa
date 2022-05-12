// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

import java.util.Objects;
import java.util.Optional;

/**
 * A container image.
 *
 * @author mpolden
 */
// TODO: Rename to ContainerImage. Compatibility with older config-models must be preserved.
public class DockerImage {

    public static final DockerImage EMPTY = new DockerImage("", "", Optional.empty());

    private final String registry;
    private final String repository;
    private final Optional<String> tag;

    DockerImage(String registry, String repository, Optional<String> tag) {
        this.registry = Objects.requireNonNull(registry, "registry must be non-null");
        this.repository = Objects.requireNonNull(repository, "repository must be non-null");
        this.tag = Objects.requireNonNull(tag, "tag must be non-null");

        if (tag.isPresent() && tag.get().isBlank())
            throw new IllegalArgumentException("Set tag cannot be empty");
    }

    /** Returns the registry-part of this, i.e. the host/port of the registry. */
    public String registry() {
        return registry;
    }

    /** Returns the repository-part of this */
    public String repository() {
        return repository;
    }

    /** Returns the registry and repository for this image, excluding its tag */
    public String untagged() {
        return new DockerImage(registry, repository, Optional.empty()).asString();
    }

    /** Returns this image's tag, if any */
    public Optional<String> tag() {
        return tag;
    }

    /** Returns the tag as a {@link Version}, {@link Version#emptyVersion} if tag is not set */
    public Version tagAsVersion() {
        return tag.map(Version::new).orElse(Version.emptyVersion);
    }

    /** Returns a copy of this tagged with the given version */
    public DockerImage withTag(Version version) {
        return new DockerImage(registry, repository, Optional.of(version.toFullString()));
    }

    /** Returns a copy of this with registry set to given value */
    public DockerImage withRegistry(String registry) {
        if (registry.isBlank()) throw new IllegalArgumentException("Registry cannot be empty");
        if (registry.charAt(registry.length() - 1) == '/') throw new IllegalArgumentException("Registry cannot end with '/': " + registry);
        return new DockerImage(registry, repository, tag);
    }

    public String asString() {
        if (equals(EMPTY)) return "";
        return registry + "/" + repository + tag.map(t -> ':' + t).orElse("");
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DockerImage that = (DockerImage) o;
        return registry.equals(that.registry) &&
               repository.equals(that.repository) &&
               tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, repository, tag);
    }

    public static DockerImage fromString(String s) {
        if (s.isEmpty()) return EMPTY;

        int repositoryStart = s.lastIndexOf('/', s.lastIndexOf('/') - 1);
        if (repositoryStart < 0) throw new IllegalArgumentException("Expected to find at least 2 path segments in: " + s);

        String registry = s.substring(0, repositoryStart);
        String repository = s.substring(repositoryStart + 1);

        int tagStart = repository.indexOf(':');
        Optional<String> tag = tagStart < 0 ? Optional.empty() : Optional.of(repository.substring(tagStart + 1));

        if (tagStart >= 0) repository = repository.substring(0, tagStart);
        if (repository.isEmpty()) throw new IllegalArgumentException("Repository must be non-empty in '" + s + "'");
        return new DockerImage(registry, repository, tag);
    }

}
