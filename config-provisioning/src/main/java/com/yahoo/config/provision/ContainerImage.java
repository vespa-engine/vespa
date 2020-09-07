// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

import java.util.Objects;
import java.util.Optional;

/**
 * A Docker image.
 *
 * @author mpolden
 */
public class ContainerImage {

    public static final ContainerImage EMPTY = new ContainerImage("", Optional.empty());

    private final String repository;
    private final Optional<String> tag;

    private ContainerImage(String repository, Optional<String> tag) {
        this.repository = Objects.requireNonNull(repository, "repository must be non-null");
        this.tag = Objects.requireNonNull(tag, "tag must be non-null");
    }

    public String repository() {
        return repository;
    }

    public Optional<String> tag() {
        return tag;
    }

    /** Returns the tag as Version, {@link Version#emptyVersion} if tag is not set */
    public Version tagAsVersion() {
        return tag.map(Version::new).orElse(Version.emptyVersion);
    }

    /** Returns the Docker image tagged with the given version */
    public ContainerImage withTag(Version version) {
        return new ContainerImage(repository, Optional.of(version.toFullString()));
    }

    public String asString() {
        return repository + tag.map(t -> ':' + t).orElse("");
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerImage that = (ContainerImage) o;
        return repository.equals(that.repository) &&
                tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repository, tag);
    }

    public static ContainerImage fromString(String name) {
        if (name.isEmpty()) return EMPTY;

        int n = name.lastIndexOf(':');
        if (n < 0) return new ContainerImage(name, Optional.empty());

        String tag = name.substring(n + 1);
        if (!tag.contains("/")) {
            return new ContainerImage(name.substring(0, n), Optional.of(tag));
        }
        return new ContainerImage(name, Optional.empty());
    }
}
