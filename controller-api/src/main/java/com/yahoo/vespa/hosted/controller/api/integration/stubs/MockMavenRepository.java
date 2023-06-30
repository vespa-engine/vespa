// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.hosted.controller.api.integration.maven.ArtifactId;
import com.yahoo.vespa.hosted.controller.api.integration.maven.MavenRepository;
import com.yahoo.vespa.hosted.controller.api.integration.maven.Metadata;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mock repository for maven artifacts, that returns a static metadata.
 *
 * @author jonmv
 */
public class MockMavenRepository implements MavenRepository {

    public static final ArtifactId id = new ArtifactId("ai.vespa", "search");

    private final Clock clock;
    private AtomicReference<Instant> lastUpdated;
    private final List<Version> versions = new ArrayList<>();

    @Inject
    public MockMavenRepository() {
        this(Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")));
    }

    public MockMavenRepository(Clock clock) {
        this.clock = clock;
        this.lastUpdated = new AtomicReference<>(clock.instant().minusSeconds(10801));
        versions.addAll(List.of(Version.fromString("6.0"),
                                Version.fromString("6.1"),
                                Version.fromString("6.2")));
    }

    public void addVersion(Version version) {
        lastUpdated.set(clock.instant());
        versions.add(version);
    }

    @Override
    public Metadata metadata() {
        return new Metadata(id, lastUpdated.get(), versions);
    }

    @Override
    public ArtifactId artifactId() {
        return id;
    }

}
