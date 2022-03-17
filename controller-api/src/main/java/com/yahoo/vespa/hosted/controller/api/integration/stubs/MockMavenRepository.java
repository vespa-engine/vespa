// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.maven.ArtifactId;
import com.yahoo.vespa.hosted.controller.api.integration.maven.Metadata;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock repository for maven artifacts, that returns a static metadata.
 *
 * @author jonmv
 */
public class MockMavenRepository implements MavenRepository {

    public static final ArtifactId id = new ArtifactId("ai.vespa", "search");

    private final List<Version> versions = new ArrayList<>();

    public MockMavenRepository() {
        versions.addAll(List.of(Version.fromString("6.0"),
                                Version.fromString("6.1"),
                                Version.fromString("6.2")));
    }

    public void addVersion(Version version) {
        versions.add(version);
    }

    @Override
    public Metadata metadata() {
        return new Metadata(id, versions);
    }

    @Override
    public ArtifactId artifactId() {
        return id;
    }

}
