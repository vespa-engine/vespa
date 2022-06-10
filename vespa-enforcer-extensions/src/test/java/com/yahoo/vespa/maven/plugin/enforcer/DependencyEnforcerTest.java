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
class DependencyEnforcerTest {

    @Test
    void succeeds_when_all_dependencies_and_rules_match() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "container-core", "8.0.0", "provided"),
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        Set<String> rules = Set.of(
                "com.yahoo.vespa:container-core:*:jar:provided",
                "com.yahoo.vespa:*:*:jar:test");
        assertDoesNotThrow(() -> DependencyEnforcer.validateDependencies(dependencies, rules, true));
    }

    @Test
    void fails_on_unmatched_dependency() {
        Set<Artifact> dependencies = Set.of(
                artifact("com.yahoo.vespa", "container-core", "8.0.0", "provided"),
                artifact("com.yahoo.vespa", "testutils", "8.0.0", "test"));
        Set<String> rules = Set.of("com.yahoo.vespa:*:*:jar:test");
        EnforcerRuleException exception = assertThrows(
                EnforcerRuleException.class,
                () -> DependencyEnforcer.validateDependencies(dependencies, rules, true));
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
                "com.yahoo.vespa:container-core:*:jar:provided",
                "com.yahoo.vespa:*:*:jar:test");
        EnforcerRuleException exception = assertThrows(
                EnforcerRuleException.class,
                () -> DependencyEnforcer.validateDependencies(dependencies, rules, true));
        String expectedErrorMessage =
                """
                Vespa dependency enforcer failed:
                Rules not matching any dependency:
                 - com.yahoo.vespa:container-core:*:jar:provided
                """;
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope) {
        return new DefaultArtifact(
                groupId, artifactId, version, scope, "jar", /*classifier*/null, new DefaultArtifactHandler("jar"));
    }

}