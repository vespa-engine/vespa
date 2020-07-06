// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;


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
    public void findsAllTestProvidedDependencies() {
        Map<String, Artifact> artifacts = new TreeMap<>();
        Artifact a = createArtifact(artifacts, "a", "compile", List.of());
        Artifact aa = createArtifact(artifacts, "a-a", "compile", List.of("a"));
        Artifact ab = createArtifact(artifacts, "a-b", "runtime", List.of("a"));
        Artifact aba = createArtifact(artifacts, "a-b-a", "runtime", List.of("a", "a-b"));
        Artifact ac = createArtifact(artifacts, "a-c", "runtime", List.of("a"));
        Artifact ad = createArtifact(artifacts, "a-d", "compile", List.of("a"));
        Artifact ada = createArtifact(artifacts, "a-d-a", "compile", List.of("a", "a-d"));
        Artifact adb = createArtifact(artifacts, "a-d-b", "compile", List.of("a", "a-d"));
        Artifact b = createArtifact(artifacts, "b", "provided", List.of());
        Artifact ba = createArtifact(artifacts, "b-a", "provided", List.of("b"));
        Artifact bb = createArtifact(artifacts, "b-b", "provided", List.of("b"));
        Artifact c = createArtifact(artifacts, "c", "runtime", List.of());
        Artifact ca = createArtifact(artifacts, "c-a", "runtime", List.of("c"));
        Artifact d = createArtifact(artifacts, "d", "test", List.of());
        Artifact da = createArtifact(artifacts, "d-a", "test", List.of("d"));
        Artifact daa = createArtifact(artifacts, "d-a-a", "test", List.of("d", "d-a"));
        Artifact db = createArtifact(artifacts, "d-b", "test", List.of("d"));
        Artifact dc = createArtifact(artifacts, "d-c", "test", List.of("d"));
        Artifact dca = createArtifact(artifacts, "d-c-a", "test", List.of("d", "d-c"));

        String configString =
                "com.test:a-d:compile," +
                "com.test:a:provided," +
                "com.test:d-a:test," +
                "com.test:d-c:compile," +
                "com.test:d:runtime";
        TestBundleDependencyScopeTranslator translator = TestBundleDependencyScopeTranslator.from(artifacts, configString);
        assertScope(translator, a, "provided");
        assertScope(translator, aa, "provided");
        assertScope(translator, ab, "provided");
        assertScope(translator, aba, "provided");
        assertScope(translator, ac, "provided");
        assertScope(translator, ad, "compile");
        assertScope(translator, ada, "compile");
        assertScope(translator, adb, "compile");
        assertScope(translator, b, "provided");
        assertScope(translator, ba, "provided");
        assertScope(translator, bb, "provided");
        assertScope(translator, c, "runtime");
        assertScope(translator, ca, "runtime");
        assertScope(translator, d, "runtime");
        assertScope(translator, da, "test");
        assertScope(translator, daa, "test");
        assertScope(translator, db, "runtime");
        assertScope(translator, dc, "compile");
        assertScope(translator, dca, "compile");
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