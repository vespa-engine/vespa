package com.yahoo.vespa.hosted.controller.api.integration.maven;

/**
 * A Maven repository which keeps released artifacts.
 *
 * @author jonmv
 */
public interface MavenRepository {

    /** Returns metadata about all releases of a specific artifact to this repository. */
    Metadata getMetadata();

}
