// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.maven;

import static java.util.Objects.requireNonNull;

/**
 * Identifier for an artifact.
 *
 * @author jonmv
 */
public class ArtifactId {

    private final String groupId;
    private final String artifactId;

    public ArtifactId(String groupId, String artifactId) {
        this.groupId = requireNonNull(groupId);
        this.artifactId = requireNonNull(artifactId);
    }

    /** Group ID of this. */
    public String groupId() { return groupId; }

    /** Artifact ID of this. */
    public String artifactId() { return artifactId; }

    @Override
    public String toString() { return groupId + "." + artifactId; }

}
