// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.maven.ArtifactId;
import com.yahoo.vespa.hosted.controller.api.integration.maven.Metadata;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;

import java.util.List;

/**
 * Mock repository for maven artifacts, that returns a static metadata.
 *
 * @author jonmv
 */
public class MockMavenRepository implements MavenRepository {

    public static final ArtifactId id = new ArtifactId("ai.vespa", "search");

    @Override
    public Metadata metadata() {
        return new Metadata(id, List.of(Version.fromString("6.0"),
                                        Version.fromString("6.1"),
                                        Version.fromString("6.2")));
    }

    @Override
    public ArtifactId artifactId() {
        return id;
    }

}
