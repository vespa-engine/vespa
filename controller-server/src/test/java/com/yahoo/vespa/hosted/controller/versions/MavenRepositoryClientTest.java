// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.vespa.hosted.controller.api.integration.maven.ArtifactId;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class MavenRepositoryClientTest {

    @Test
    public void testUri() {
        assertEquals(URI.create("https://domain:123/base/group/id/artifact-id/maven-metadata.xml"),
                     MavenRepositoryClient.withArtifactPath(URI.create("https://domain:123/base/"),
                                                            new ArtifactId("group.id", "artifact-id")));
    }

}
