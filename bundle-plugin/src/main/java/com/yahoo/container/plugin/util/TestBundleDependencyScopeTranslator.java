// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import org.apache.maven.artifact.Artifact;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Translates the scope of dependencies when constructing a test bundle.
 * Used by {@link Artifacts} to determine which artifacts that are provided by the runtime or must be included in the bundle.
 *
 * Dependencies of scope 'test' are by default translated to 'compile'. Dependencies of other scopes are kept as is.
 *
 * Default scope translation for 'test' scope dependencies can be overridden through a comma-separated configuration string.
 * Each substring is a triplet on the form [groupId]:[artifactId]:[scope].
 * Scope translation overrides affects all transitive dependencies.
 * The ordering of the triplets determines the priority - only the first matching override will affect a given dependency.
 *
 * @author bjorncs
 */
public class TestBundleDependencyScopeTranslator implements Artifacts.ScopeTranslator {

    private static final Logger log = Logger.getLogger(TestBundleDependencyScopeTranslator.class.getName());

    private final Map<Artifact, String> dependencyScopes;

    private TestBundleDependencyScopeTranslator(Map<Artifact, String> dependencyScopes) {
        this.dependencyScopes = dependencyScopes;
    }

    @Override
    public String scopeOf(Artifact artifact) {
        return Objects.requireNonNull(dependencyScopes.get(artifact), () -> "Could not lookup scope for " + artifact);
    }

    public static TestBundleDependencyScopeTranslator from(Collection<Artifact> dependencies, String rawConfig) {
        List<DependencyOverride> dependencyOverrides = toDependencyOverrides(rawConfig);
        Map<Artifact, String> dependencyScopes = new HashMap<>();
        Map<String, Artifact> dependenciesById = dependencies.stream().collect(toMap(Artifact::getId, Function.identity()));
        for (Artifact dependency : dependencies) {
            dependencyScopes.put(dependency, getScopeForDependency(dependency, dependencyOverrides, dependenciesById));
        }
        return new TestBundleDependencyScopeTranslator(dependencyScopes);
    }

    private static List<DependencyOverride> toDependencyOverrides(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) return List.of();
        return Arrays.stream(rawConfig.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .map(TestBundleDependencyScopeTranslator::toDependencyOverride)
                .collect(toList());
    }

    private static DependencyOverride toDependencyOverride(String overrideString) {
        String[] elements = overrideString.split(":");
        if (elements.length != 3) {
            throw new IllegalArgumentException("Invalid dependency override: " + overrideString);
        }
        return new DependencyOverride(elements[0], elements[1], elements[2]);
    }

    private static String getScopeForDependency(
            Artifact dependency, List<DependencyOverride> overrides, Map<String, Artifact> otherArtifacts) {
        String oldScope = dependency.getScope();
        if (!oldScope.equals(Artifact.SCOPE_TEST)) return oldScope;
        for (DependencyOverride override : overrides) {
            for (Artifact dependent : dependencyTrailOf(dependency, otherArtifacts)) {
                if (override.isForArtifact(dependent)) {
                    log.fine(() -> String.format(
                            "Overriding scope of '%s'; scope '%s' overridden to '%s'",
                            dependency.getId(), oldScope, override.scope));
                    return override.scope;
                }
            }
        }
        log.fine(() -> String.format(
                "Using default scope translation for '%s'; scope 'test' translated to 'compile'",
                dependency.getId()));
        return Artifact.SCOPE_COMPILE;
    }

    private static List<Artifact> dependencyTrailOf(Artifact artifact, Map<String, Artifact> otherArtifacts) {
        return artifact.getDependencyTrail().stream()
                .skip(1) // Maven project itself is the first entry
                .map(otherArtifacts::get)
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private static class DependencyOverride {
        final String groupId;
        final String artifactId;
        final String scope;

        DependencyOverride(String groupId, String artifactId, String scope) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.scope = scope;
        }

        boolean isForArtifact(Artifact artifact) {
            return artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId);
        }
    }
}
