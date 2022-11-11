package com.yahoo.vespa.maven.plugin.enforcer;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class EnforceDependenciesTest {

    @Test
    void succeeds_when_all_dependencies_and_rules_match() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "container-core", "8.0.0", "provided"),
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        Set<String> rules = Set.of(
                "com.yahoo.vespa:container-core:jar:*:provided",
                "com.yahoo.vespa:*:jar:*:test");
        assertDoesNotThrow(() -> EnforceDependencies.validateDependencies(dependencies, rules, true));
    }

    @Test
    void fails_on_unmatched_dependency() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "container-core", "8.0.0", "provided"),
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        Set<String> rules = Set.of("com.yahoo.vespa:*:jar:*:test");
        EnforcerRuleException exception = assertThrows(
                EnforcerRuleException.class,
                () -> EnforceDependencies.validateDependencies(dependencies, rules, true));
        String expectedErrorMessage =
                """
                Vespa dependency enforcer failed:
                Dependencies not matching any rule:
                 - com.yahoo.vespa:container-core:jar:8.0.0:provided
                """;
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    @Test
    void fails_on_unmatched_rule() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        Set<String> rules = Set.of(
                "com.yahoo.vespa:container-core:jar:*:provided",
                "com.yahoo.vespa:*:jar:*:test");
        EnforcerRuleException exception = assertThrows(
                EnforcerRuleException.class,
                () -> EnforceDependencies.validateDependencies(dependencies, rules, true));
        String expectedErrorMessage =
                """
                Vespa dependency enforcer failed:
                Rules not matching any dependency:
                 - com.yahoo.vespa:container-core:jar:*:provided
                """;
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    @Test
    void fails_on_version_mismatch() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        Set<String> rules = Set.of(
                "com.yahoo.vespa:testutils:jar:7.0.0:test");
        EnforcerRuleException exception = assertThrows(
                EnforcerRuleException.class,
                () -> EnforceDependencies.validateDependencies(dependencies, rules, true));
        String expectedErrorMessage =
                """
                Vespa dependency enforcer failed:
                Dependencies not matching any rule:
                 - com.yahoo.vespa:testutils:jar:8.0.0:test
                Rules not matching any dependency:
                 - com.yahoo.vespa:testutils:jar:7.0.0:test
                """;
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    @Test
    void fails_on_scope_mismatch() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        Set<String> rules = Set.of(
                "com.yahoo.vespa:testutils:jar:8.0.0:provided");
        EnforcerRuleException exception = assertThrows(
                EnforcerRuleException.class,
                () -> EnforceDependencies.validateDependencies(dependencies, rules, true));
        String expectedErrorMessage =
                """
                Vespa dependency enforcer failed:
                Dependencies not matching any rule:
                 - com.yahoo.vespa:testutils:jar:8.0.0:test
                Rules not matching any dependency:
                 - com.yahoo.vespa:testutils:jar:8.0.0:provided
                """;
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    @Test
    void matches_shorter_rule_variant_without_type() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        assertDoesNotThrow(() -> EnforceDependencies.validateDependencies(
                dependencies, Set.of("com.yahoo.vespa:testutils:jar:8.0.0:test"), true));
        assertDoesNotThrow(() -> EnforceDependencies.validateDependencies(
                dependencies, Set.of("com.yahoo.vespa:testutils:8.0.0:test"), true));
    }

    @Test
    void matches_artifact_with_classifier() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.google.inject", "guice", "4.2.3", "provided", "no_aop"));
        assertDoesNotThrow(() -> EnforceDependencies.validateDependencies(
                dependencies, Set.of("com.google.inject:guice:jar:no_aop:4.2.3:provided"), true));
        EnforcerRuleException exception = assertThrows(
                EnforcerRuleException.class,
                () -> EnforceDependencies.validateDependencies(
                        dependencies, Set.of("com.google.inject:guice:4.2.3:provided"), true));
        String expectedErrorMessage =
                """
                Vespa dependency enforcer failed:
                Dependencies not matching any rule:
                 - com.google.inject:guice:jar:no_aop:4.2.3:provided
                Rules not matching any dependency:
                 - com.google.inject:guice:4.2.3:provided
                """;
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope) {
        return artifact(groupId, artifactId, version, scope, null);
    }
    private static Artifact artifact(String groupId, String artifactId, String version, String scope, String classifier) {
        return new DefaultArtifact(
                groupId, artifactId, version, scope, "jar", classifier, new DefaultArtifactHandler("jar"));
    }

}
