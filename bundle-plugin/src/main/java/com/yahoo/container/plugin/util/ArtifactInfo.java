package com.yahoo.container.plugin.util;

import org.apache.maven.artifact.Artifact;

/**
 * Helper class to work with artifacts.
 *
 * @author gjoranv
 */
public record ArtifactInfo(String groupId, String artifactId, String version) {


    public ArtifactInfo(Artifact artifact) {
        this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public static ArtifactInfo fromStringValue(String stringValue) {
        var parts = stringValue.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid artifact string: " + stringValue);
        }
        return new ArtifactInfo(parts[0], parts[1], parts[2]);
    }

    public String stringValue() {
        return groupId + ":" + artifactId + ":" + version;
    }

}
