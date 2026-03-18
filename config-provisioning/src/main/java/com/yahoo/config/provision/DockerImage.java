// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A docker/container image reference.
 *
 * @author glebashnik
 * @author Martin Polden
 */
public record DockerImage(String registry, String repository, Optional<String> tag) {
    public static final DockerImage EMPTY = new DockerImage("", "", Optional.empty());

    public DockerImage(String registry, String repository, Optional<String> tag) {
        this.registry = Objects.requireNonNull(registry, "Registry must be non-null");
        this.repository = Objects.requireNonNull(repository, "Repository must be non-null");
        this.tag = Objects.requireNonNull(tag, "Tag must be non-null");

        if (registry.isEmpty() && repository.isEmpty() && tag.isEmpty()) {
            // This is EMPTY
            return;
        }

        // Otherwise ensure we don't create invalid images.
        validateRegistry(registry);
        validateRepository(repository);
        validateTag(tag);
    }
    /** Returns the registry and repository for this image, excluding its tag */
    public String untagged() {
        return new DockerImage(registry, repository, Optional.empty()).asString();
    }

    /** Returns the tag as a {@link Version}, {@link Version#emptyVersion} if tag is not set */
    public Version tagAsVersion() {
        return tag.map(Version::new).orElse(Version.emptyVersion);
    }

    public DockerImage withRegistry(String registry) {
        return new DockerImage(registry, this.repository, this.tag);
    }

    public DockerImage withRepository(String repository) {
        return new DockerImage(this.registry, repository, this.tag);
    }

    public DockerImage withTag(Optional<String> tag) {
        return new DockerImage(this.registry, this.repository, tag);
    }

    public DockerImage withTag(Version version) {
        return new DockerImage(this.registry, this.repository, Optional.of(version.toFullString()));
    }

    // Registry pattern per https://github.com/distribution/reference
    // ip4 is part of the domain name pattern, no need for a separate pattern.
    private static final String DOMAIN_NAME_COMPONENT = "(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])";
    private static final String DOMAIN_NAME = DOMAIN_NAME_COMPONENT + "(?:\\." + DOMAIN_NAME_COMPONENT + ")*\\.?";
    private static final String IPV6_ADDRESS = "\\[(?:[a-fA-F0-9:]+)\\]";
    private static final String HOST = "(?:" + DOMAIN_NAME + "|" + IPV6_ADDRESS + ")";
    private static final String PORT = "(?::[0-9]+)?";
    private static final Pattern REGISTRY_PATTERN = Pattern.compile(HOST + PORT);

    // Repository and tag patterns per OCI distribution spec
    // (https://github.com/opencontainers/distribution-spec/blob/main/spec.md)
    private static final String PATH_COMPONENT = "[a-z0-9]+(?:(?:[._]|__|[-]+)[a-z0-9]+)*";
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile(PATH_COMPONENT + "(?:/" + PATH_COMPONENT + ")*");
    private static final Pattern TAG_PATTERN = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}");

    private static void validateRegistry(String registry) {
        if (!REGISTRY_PATTERN.matcher(registry).matches()) {
            throw new IllegalArgumentException("Invalid registry: " + registry);
        }
    }

    private static void validateRepository(String repository) {
        if (!REPOSITORY_PATTERN.matcher(repository).matches()) {
            throw new IllegalArgumentException("Invalid repository: " + repository);
        }
    }

    private static void validateTag(Optional<String> tag) {
        if (tag.isPresent() && !TAG_PATTERN.matcher(tag.get()).matches()) {
            throw new IllegalArgumentException("Invalid tag: " + tag.get());
        }
    }

    public String asString() {
        if (equals(EMPTY)) {
            return "";
        }

        return registry + "/" + repository + tag.map(t -> ':' + t).orElse("");
    }

    @Override
    public String toString() {
        return asString();
    }

    public static DockerImage fromString(String s) {
        if (s.isEmpty()) return EMPTY;

        int repositoryStart = s.indexOf('/');
        if (repositoryStart < 0) {
            throw new IllegalArgumentException("Image reference requires at least one / in: " + s);
        }

        String registry = s.substring(0, repositoryStart);
        String repository = s.substring(repositoryStart + 1);

        int tagStart = repository.indexOf(':');
        Optional<String> tag = Optional.empty();

        if (tagStart >= 0) {
            tag = Optional.of(repository.substring(tagStart + 1));
            repository = repository.substring(0, tagStart);
        }

        return new DockerImage(registry, repository, tag);
    }
}
