// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
class Artifacts {
    static class ArtifactSet {
        private final List<Artifact> jarArtifactsToInclude;
        private final List<Artifact> jarArtifactsProvided;
        private final List<Artifact> nonJarArtifacts;

        private ArtifactSet(
                List<Artifact> jarArtifactsToInclude,
                List<Artifact> jarArtifactsProvided,
                List<Artifact> nonJarArtifacts) {
            this.jarArtifactsToInclude = jarArtifactsToInclude;
            this.jarArtifactsProvided = jarArtifactsProvided;
            this.nonJarArtifacts = nonJarArtifacts;
        }

        List<Artifact> getJarArtifactsToInclude() {
            return jarArtifactsToInclude;
        }

        List<Artifact> getJarArtifactsProvided() {
            return jarArtifactsProvided;
        }

        List<Artifact> getNonJarArtifacts() {
            return nonJarArtifacts;
        }
    }

    static ArtifactSet getArtifacts(MavenProject project) { return getArtifacts(project, false, null); }

    static ArtifactSet getArtifacts(MavenProject project, boolean includeTestArtifacts, String testProvidedConfig) {
        TestProvidedArtifacts testProvidedArtifacts = TestProvidedArtifacts.from(project.getArtifactMap(), testProvidedConfig);
        List<Artifact> jarArtifactsToInclude = new ArrayList<>();
        List<Artifact> jarArtifactsProvided = new ArrayList<>();
        List<Artifact> nonJarArtifactsToInclude = new ArrayList<>();
        List<Artifact> nonJarArtifactsProvided = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            if ("jar".equals(artifact.getType())) {
                if (includeTestArtifacts && testProvidedArtifacts.isTestProvided(artifact)) {
                    jarArtifactsProvided.add(artifact);
                } else if (Artifact.SCOPE_COMPILE.equals(artifact.getScope())) {
                    jarArtifactsToInclude.add(artifact);
                } else if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                    jarArtifactsProvided.add(artifact);
                } else if (includeTestArtifacts && Artifact.SCOPE_TEST.equals(artifact.getScope())) {
                    jarArtifactsToInclude.add(artifact);
                }
            } else {
                if (Artifact.SCOPE_COMPILE.equals(artifact.getScope())) {
                    nonJarArtifactsToInclude.add(artifact);
                } else if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                    nonJarArtifactsProvided.add(artifact);
                }
            }
        }
        nonJarArtifactsToInclude.addAll(nonJarArtifactsProvided);
        return new ArtifactSet(jarArtifactsToInclude, jarArtifactsProvided, nonJarArtifactsToInclude);
    }

    static Collection<Artifact> getArtifactsToInclude(MavenProject project) {
        return getArtifacts(project, false, null).getJarArtifactsToInclude();
    }
}
