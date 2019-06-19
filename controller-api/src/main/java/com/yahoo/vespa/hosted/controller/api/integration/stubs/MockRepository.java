package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.maven.ArtifactId;
import com.yahoo.vespa.hosted.controller.api.integration.maven.Metadata;
import com.yahoo.vespa.hosted.controller.api.integration.maven.Repository;

import java.util.List;

/**
 * Mock repository for maven artifacts, that returns a static metadata.
 *
 * @author jonmv
 */
public class MockRepository implements Repository {

    @Override
    public Metadata getMetadata(ArtifactId id) {
        return new Metadata(id, List.of(Version.fromString("3.2.1"),
                                        Version.fromString("1.2.3")));
    }

}
