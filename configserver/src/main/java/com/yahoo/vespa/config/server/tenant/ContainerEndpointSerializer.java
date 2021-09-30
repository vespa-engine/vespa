// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains all methods for de-/serializing ContainerEndpoints to/from JSON.
 * Also supports de-/serializing lists of these values.
 *
 * @author ogronnesby
 */
public class ContainerEndpointSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String clusterIdField = "clusterId";
    private static final String namesField = "names";

    private ContainerEndpointSerializer() {}

    public static ContainerEndpoint endpointFromSlime(Inspector inspector) {
        final var clusterId = inspector.field(clusterIdField).asString();
        final var namesInspector = inspector.field(namesField);

        if (clusterId.isEmpty()) {
            throw new IllegalStateException("'clusterId' missing on serialized ContainerEndpoint");
        }

        if (! namesInspector.valid()) {
            throw new IllegalStateException("'names' missing on serialized ContainerEndpoint");
        }

        final var names = new ArrayList<String>();

        namesInspector.traverse((ArrayTraverser) (idx, nameInspector) -> {
            final var containerName = nameInspector.asString();
            names.add(containerName);
        });

        return new ContainerEndpoint(clusterId, names);
    }

    public static List<ContainerEndpoint> endpointListFromSlime(Slime slime) {
        final var inspector = slime.get();
        return endpointListFromSlime(inspector);
    }
    public static List<ContainerEndpoint> endpointListFromSlime(Inspector inspector) {
        final var endpoints = new ArrayList<ContainerEndpoint>();

        inspector.traverse((ArrayTraverser) (idx, endpointInspector) -> {
            final var containerEndpoint = endpointFromSlime(endpointInspector);
            endpoints.add(containerEndpoint);
        });

        return endpoints;
    }


    public static void endpointToSlime(Cursor cursor, ContainerEndpoint endpoint) {
        cursor.setString(clusterIdField, endpoint.clusterId());

        final var namesInspector = cursor.setArray(namesField);
        endpoint.names().forEach(namesInspector::addString);
    }

    public static Slime endpointListToSlime(List<ContainerEndpoint> endpoints) {
        final var slime = new Slime();
        final var cursor = slime.setArray();

        endpoints.forEach(endpoint -> {
            final var endpointCursor = cursor.addObject();
            endpointToSlime(endpointCursor, endpoint);
        });

        return slime;
    }

}
