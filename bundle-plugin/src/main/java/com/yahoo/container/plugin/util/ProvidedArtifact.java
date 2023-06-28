package com.yahoo.container.plugin.util;

import org.apache.maven.artifact.Artifact;

/**
 * Helper class to work with artifacts provided by the container.
 *
 * @author gjoranv
 */
public record ProvidedArtifact(String groupId, String artifactId, String version) {


    public ProvidedArtifact(Artifact artifact) {
        this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public String stringValue() {
        return groupId + ":" + artifactId + ":" + version;
    }

}
