// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;


import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class TestProvidedArtifactsTest {

    private static final String GROUP_ID = "com.test";

    @Test
    public void findsAllTestProvidedDependencies() {
        Map<String, Artifact> artifacts = new TreeMap<>();
        Artifact a = createArtifact(artifacts, "a");
        Artifact aa = createArtifact(artifacts, "a-a", "a");
        Artifact ab = createArtifact(artifacts, "a-b", "a");
        Artifact aaa = createArtifact(artifacts, "a-a-a", "a", "a-a");
        Artifact b = createArtifact(artifacts, "b");
        Artifact ba = createArtifact(artifacts, "b-a", "b");
        Artifact c = createArtifact(artifacts, "c");

        String configString = "com.test:a,com.test:b-a,!com.test:a-b";
        TestProvidedArtifacts testProvidedArtifacts = TestProvidedArtifacts.from(artifacts, configString);

        assertTrue(testProvidedArtifacts.isTestProvided(a));
        assertTrue(testProvidedArtifacts.isTestProvided(aa));
        assertFalse(testProvidedArtifacts.isTestProvided(ab));
        assertTrue(testProvidedArtifacts.isTestProvided(aaa));
        assertFalse(testProvidedArtifacts.isTestProvided(b));
        assertTrue(testProvidedArtifacts.isTestProvided(ba));
        assertFalse(testProvidedArtifacts.isTestProvided(c));
    }

    private static Artifact createArtifact(Map<String, Artifact> artifacts, String artifactId, String... dependents) {
        Artifact artifact = createArtifact(artifactId, dependents);
        artifacts.put(simpleId(artifactId), artifact);
        return artifact;
    }

    private static Artifact createArtifact(String artifactId, String... dependents) {
        Artifact artifact = new DefaultArtifact(GROUP_ID, artifactId, "1.0", "test", "jar", "deploy", new DefaultArtifactHandler("jar"));
        List<String> dependencyTrail = new ArrayList<>();
        dependencyTrail.add(fullId("bundle-plugin"));
        Arrays.stream(dependents).forEach(dependent -> dependencyTrail.add(fullId(dependent)));
        dependencyTrail.add(fullId(artifactId));
        artifact.setDependencyTrail(dependencyTrail);
        return artifact;
    }

    private static String fullId(String artifactId) { return simpleId(artifactId) + ":1.0:compile"; }
    private static String simpleId(String artifactId) { return GROUP_ID + ":" + artifactId; }

}