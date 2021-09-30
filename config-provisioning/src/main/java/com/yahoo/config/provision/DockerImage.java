// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    public static final DockerImage EMPTY = new DockerImage("", "", Optional.empty(), Optional.empty());

    private final String registry;
    private final String repository;
    private final Optional<String> tag;
    private final Optional<DockerImage> replacedBy;

    DockerImage(String registry, String repository, Optional<String> tag, Optional<DockerImage> replacedBy) {
        this.registry = Objects.requireNonNull(registry, "registry must be non-null");
        this.repository = Objects.requireNonNull(repository, "repository must be non-null");
        this.tag = Objects.requireNonNull(tag, "tag must be non-null");
        this.replacedBy = Objects.requireNonNull(replacedBy);
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
        return new DockerImage(registry, repository, Optional.empty(), replacedBy).asString();
    }

    /** Returns this image's tag, if any */
    public Optional<String> tag() {
        return tag;
    }

    /** Returns the tag as a {@link Version}, {@link Version#emptyVersion} if tag is not set */
    public Version tagAsVersion() {
        return tag.map(Version::new).orElse(Version.emptyVersion);
    }

    /** The image that replaces this, if any */
    public Optional<DockerImage> replacedBy() {
        return replacedBy;
    }

    /** Returns a copy of this tagged with the given version */
    public DockerImage withTag(Version version) {
        return new DockerImage(registry, repository, Optional.of(version.toFullString()), replacedBy);
    }

    /** Returns a copy of this with registry set to given value */
    public DockerImage withRegistry(String registry) {
        return new DockerImage(registry, repository, tag, replacedBy);
    }

    /** Returns a copy of this with replacement image set to given value */
    public DockerImage withReplacedBy(DockerImage image) {
        return new DockerImage(registry, repository, tag, Optional.of(image).filter(i -> !i.equals(EMPTY)));
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
               tag.equals(that.tag) &&
               replacedBy.equals(that.replacedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, repository, tag, replacedBy);
    }

    public static DockerImage from(String registry, String repository) {
        return new DockerImage(registry, repository, Optional.empty(), Optional.empty());
    }

    public static DockerImage fromString(String s) {
        if (s.isEmpty()) return EMPTY;

        int firstPathSeparator = s.indexOf('/');
        if (firstPathSeparator < 0) throw new IllegalArgumentException("Missing path separator in '" + s + "'");

        String registry = s.substring(0, firstPathSeparator);
        String repository = s.substring(firstPathSeparator + 1);
        if (repository.isEmpty()) throw new IllegalArgumentException("Repository must be non-empty in '" + s + "'");

        int tagStart = repository.indexOf(':');
        if (tagStart < 0) return new DockerImage(registry, repository, Optional.empty(), Optional.empty());

        String tag = repository.substring(tagStart + 1);
        repository = repository.substring(0, tagStart);
        return new DockerImage(registry, repository, Optional.of(tag), Optional.empty());
    }

}
