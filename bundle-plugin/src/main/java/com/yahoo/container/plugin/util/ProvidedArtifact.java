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

    public static ProvidedArtifact fromStringValue(String stringValue) {
        var parts = stringValue.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid artifact string: " + stringValue);
        }
        return new ProvidedArtifact(parts[0], parts[1], parts[2]);
    }

    public String stringValue() {
        return groupId + ":" + artifactId + ":" + version;
    }

}
