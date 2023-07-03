package com.yahoo.container.plugin.util;

import org.apache.maven.artifact.Artifact;

import java.util.Objects;

import static com.yahoo.container.plugin.util.Artifacts.VESPA_GROUP_ID;

/**
 * Helper class to work with artifacts. Vespa artifacts have their version set to '*'.
 *
 * @author gjoranv
 */
public class ArtifactInfo {

    private final String groupId;
    private final String artifactId;
    private final String version;

    private ArtifactInfo(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = VESPA_GROUP_ID.equals(groupId) ? "*" : version;
    }

    public static ArtifactInfo fromArtifact(Artifact artifact) {
        return new ArtifactInfo(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public static ArtifactInfo fromStringValue(String stringValue) {
        var parts = stringValue.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid artifact string: " + stringValue);
        }
        return new ArtifactInfo(parts[0], parts[1], parts[2]);
    }

    public String stringValue() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public String toString() {
        return stringValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactInfo that = (ArtifactInfo) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }
}
