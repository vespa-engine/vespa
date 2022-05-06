// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.artifact;


import java.util.List;

/**
 * A registry of artifacts (e.g. container image or RPM).
 *
 * @author mpolden
 */
public interface ArtifactRegistry {

    /** Delete all given artifacts */
    void deleteAll(List<Artifact> artifacts);

    /** Returns a list of all artifacts in this system */
    List<Artifact> list();

}
