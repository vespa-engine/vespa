// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContainerEndpointsCacheTest {
    @Test
    public void readWriteFromCache() {
        final var cache = new ContainerEndpointsCache(Path.createRoot(), new MockCurator());
        final var endpoints = List.of(
                new ContainerEndpoint("the-cluster-1", ApplicationClusterEndpoint.Scope.global, List.of("a", "b", "c"))
        );

        cache.write(ApplicationId.defaultId(), endpoints);

        final var deserialized = cache.read(ApplicationId.defaultId());

        assertEquals(endpoints, deserialized);
    }

    @Test
    public void readingNonExistingEntry() {
        final var cache = new ContainerEndpointsCache(Path.createRoot(), new MockCurator());
        final var endpoints = cache.read(ApplicationId.defaultId());
        assertTrue(endpoints.isEmpty());
    }
}