// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import org.apache.maven.artifact.Artifact;

import java.util.Objects;

/**
 * Helper class to work with artifacts, where version does not matter.
 *
 * @author gjoranv
 */
public class ArtifactId {

    private final String groupId;
    private final String artifactId;

    private ArtifactId(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public static ArtifactId fromArtifact(Artifact artifact) {
        return new ArtifactId(artifact.getGroupId(), artifact.getArtifactId());
    }

    public static ArtifactId fromStringValue(String stringValue) {
        var parts = stringValue.trim().split(":");
        if (parts.length == 2 || parts.length == 3) {
            return new ArtifactId(parts[0], parts[1]);
        }
        throw new IllegalArgumentException(
                "An artifact should be represented in the format 'groupId:ArtifactId[:version]', not: " + stringValue);
    }

    public String stringValue() {
        return groupId + ":" + artifactId;
    }

    @Override
    public String toString() {
        return stringValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactId that = (ArtifactId) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId);
    }
}
