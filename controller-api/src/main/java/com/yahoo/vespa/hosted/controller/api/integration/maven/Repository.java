package com.yahoo.vespa.hosted.controller.api.integration.maven;

/**
 * A Maven repository which keeps released artifacts.
 *
 * @author jonmv
 */
public interface Repository {

    Metadata getMetadata(ArtifactId id);

}
