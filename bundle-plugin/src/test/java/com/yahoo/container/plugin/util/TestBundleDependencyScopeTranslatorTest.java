// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;


import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class TestBundleDependencyScopeTranslatorTest {

    private static final String GROUP_ID = "com.test";

    @Test
    public void test_dependencies_are_translated_to_compile_scope_by_default() {
        Map<String, Artifact> artifacts = new TreeMap<>();
        Artifact a = createArtifact(artifacts, "a", "test", List.of());
        Artifact aa = createArtifact(artifacts, "a-a", "test", List.of("a"));
        Artifact ab = createArtifact(artifacts, "a-b", "test", List.of("a"));
        Artifact aba = createArtifact(artifacts, "a-b-a", "test", List.of("a", "a-b"));

        TestBundleDependencyScopeTranslator translator = TestBundleDependencyScopeTranslator.from(artifacts, null);
        assertScope(translator, a, "compile");
        assertScope(translator, aa, "compile");
        assertScope(translator, ab, "compile");
        assertScope(translator, aba, "compile");

    }

    @Test
    public void non_test_scope_dependencies_keep_original_scope() {
        Map<String, Artifact> artifacts = new TreeMap<>();
        Artifact a = createArtifact(artifacts, "a", "provided", List.of());
        Artifact aa = createArtifact(artifacts, "a-a", "provided", List.of("a"));
        Artifact ab = createArtifact(artifacts, "a-b", "provided", List.of("a"));
        Artifact b = createArtifact(artifacts, "b", "runtime", List.of());
        Artifact ba = createArtifact(artifacts, "b-a", "runtime", List.of("b"));
        Artifact c = createArtifact(artifacts, "c", "test", List.of());
        Artifact ca = createArtifact(artifacts, "c-a", "test", List.of("c"));

        TestBundleDependencyScopeTranslator translator = TestBundleDependencyScopeTranslator.from(artifacts, null);
        assertScope(translator, a, "provided");
        assertScope(translator, aa, "provided");
        assertScope(translator, ab, "provided");
        assertScope(translator, b, "runtime");
        assertScope(translator, ba, "runtime");
        assertScope(translator, c, "compile");
        assertScope(translator, ca, "compile");
    }

    @Test
    public void ordering_in_config_string_determines_translation() {
        Map<String, Artifact> artifacts = new TreeMap<>();
        Artifact a = createArtifact(artifacts, "a", "test", List.of());
        Artifact aa = createArtifact(artifacts, "a-a", "test", List.of("a"));
        {
            String configString =
                    "com.test:a-a:runtime," +
                    "com.test:a:test,";
            TestBundleDependencyScopeTranslator translator = TestBundleDependencyScopeTranslator.from(artifacts, configString);
            assertScope(translator, a, "test");
            assertScope(translator, aa, "runtime");
        }
        {
            String configString =
                    "com.test:a:test," +
                    "com.test:a-a:runtime";
            TestBundleDependencyScopeTranslator translator = TestBundleDependencyScopeTranslator.from(artifacts, configString);
            assertScope(translator, a, "test");
            assertScope(translator, aa, "test");
        }
    }

    @Test
    public void transitive_non_test_dependencies_of_test_dependencies_keep_original_scope() {
        Map<String, Artifact> artifacts = new TreeMap<>();
        Artifact a = createArtifact(artifacts, "a", "test", List.of());
        Artifact aa = createArtifact(artifacts, "a-a", "test", List.of("a"));
        Artifact ab = createArtifact(artifacts, "a-b", "test", List.of("a"));
        Artifact aba = createArtifact(artifacts, "a-b-a", "compile", List.of("a", "a-b"));
        Artifact ac = createArtifact(artifacts, "a-c", "runtime", List.of("a"));
        Artifact b = createArtifact(artifacts, "b", "test", List.of());
        Artifact ba = createArtifact(artifacts, "b-a", "test", List.of("b"));
        Artifact bb = createArtifact(artifacts, "b-b", "provided", List.of("b"));

        String configString = "com.test:a:provided";
        TestBundleDependencyScopeTranslator translator = TestBundleDependencyScopeTranslator.from(artifacts, configString);
        assertScope(translator, a, "provided");
        assertScope(translator, aa, "provided");
        assertScope(translator, ab, "provided");
        assertScope(translator, aba, "compile");
        assertScope(translator, ac, "runtime");
        assertScope(translator, b, "compile");
        assertScope(translator, ba, "compile");
        assertScope(translator, bb, "provided");
    }

    private static Artifact createArtifact(
            Map<String, Artifact> artifacts, String artifactId, String scope, List<String> transitiveDependents) {
        Artifact artifact = createArtifact(artifactId, scope, transitiveDependents);
        artifacts.put(simpleId(artifactId), artifact);
        return artifact;
    }

    private static Artifact createArtifact(String artifactId, String scope, List<String> transitiveDependents) {
        Artifact artifact = new DefaultArtifact(
                GROUP_ID, artifactId, "1.0", scope, "jar", /*classifier*/null, new DefaultArtifactHandler("jar"));
        List<String> dependencyTrail = new ArrayList<>();
        dependencyTrail.add(GROUP_ID + "my-project:container-plugin:1-SNAPSHOT");
        transitiveDependents.forEach(dependent -> dependencyTrail.add(fullId(dependent)));
        dependencyTrail.add(fullId(artifactId));
        artifact.setDependencyTrail(dependencyTrail);
        return artifact;
    }

    private static void assertScope(
            TestBundleDependencyScopeTranslator translator, Artifact artifact, String expectedScope) {
        assertEquals(expectedScope, translator.scopeOf(artifact));
    }

    private static String fullId(String artifactId) { return simpleId(artifactId) + ":jar:1.0"; }
    private static String simpleId(String artifactId) { return GROUP_ID + ":" + artifactId; }

}