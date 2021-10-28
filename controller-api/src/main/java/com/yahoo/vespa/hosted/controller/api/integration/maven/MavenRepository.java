// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.maven;

/**
 * A Maven repository which keeps released artifacts.
 *
 * @author jonmv
 */
public interface MavenRepository {

    /** Returns metadata about all releases of a specific artifact to this repository. */
    Metadata metadata();

    /** Returns the id of the artifact whose releases this tracks. */
    ArtifactId artifactId();

}
