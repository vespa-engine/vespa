package com.yahoo.vespa.hosted.controller.api.integration.maven;

/**
 * A Maven repository which keeps released artifacts.
 *
 * @author jonmv
 */
public interface MavenRepository {

    Metadata getMetadata(ArtifactId id);

}
