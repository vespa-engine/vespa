// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.slime.Slime;
import org.junit.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;

/**
 * @author ogronnesby
 */
public class ContainerEndpointSerializerTest {

    @Test
    public void readSingleEndpoint() {
        final var slime = new Slime();
        final var entry = slime.setObject();

        entry.setString("clusterId", "foobar");
        entry.setString("scope", "application");
        final var entryNames = entry.setArray("names");
        entryNames.addString("a");
        entryNames.addString("b");

        final var endpoint = ContainerEndpointSerializer.endpointFromSlime(slime.get());
        assertEquals("foobar", endpoint.clusterId());
        assertEquals(ApplicationClusterEndpoint.Scope.application, endpoint.scope());
        assertEquals(List.of("a", "b"), endpoint.names());
    }

    @Test
    public void writeReadSingleEndpoint() {
        final var endpoint = new ContainerEndpoint("foo", ApplicationClusterEndpoint.Scope.global, List.of("a", "b"), OptionalInt.of(1));
        final var serialized = new Slime();
        ContainerEndpointSerializer.endpointToSlime(serialized.setObject(), endpoint);
        final var deserialized = ContainerEndpointSerializer.endpointFromSlime(serialized.get());

        assertEquals(endpoint, deserialized);
    }

    @Test
    public void writeReadEndpoints() {
        final var endpoints = List.of(new ContainerEndpoint("foo", ApplicationClusterEndpoint.Scope.global, List.of("a", "b"), OptionalInt.of(3), ApplicationClusterEndpoint.RoutingMethod.shared));
        final var serialized = ContainerEndpointSerializer.endpointListToSlime(endpoints);
        final var deserialized = ContainerEndpointSerializer.endpointListFromSlime(serialized);

        assertEquals(endpoints, deserialized);
    }

}
