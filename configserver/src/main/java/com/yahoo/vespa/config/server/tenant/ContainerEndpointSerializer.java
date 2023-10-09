// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

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
    private static final String scopeField = "scope";
    private static final String namesField = "names";
    private static final String weightField = "weight";
    private static final String routingMethodField = "routingMethod";
    private static final String authMethodField = "authMethod";

    private ContainerEndpointSerializer() {}

    public static ContainerEndpoint endpointFromSlime(Inspector inspector) {
        String clusterId = inspector.field(clusterIdField).asString();
        String scope = inspector.field(scopeField).asString();
        Inspector namesInspector = inspector.field(namesField);
        OptionalInt weight = SlimeUtils.optionalInteger(inspector.field(weightField));
        // assign default routingmethod. Remove when 7.507 is latest version
        // Cannot be used before all endpoints are assigned explicit routing method (from controller)
        ApplicationClusterEndpoint.RoutingMethod routingMethod = SlimeUtils.optionalString(inspector.field(routingMethodField))
                                                                           .map(ContainerEndpointSerializer::routingMethodFrom)
                                                                           .orElse(ApplicationClusterEndpoint.RoutingMethod.sharedLayer4);
        ApplicationClusterEndpoint.AuthMethod authMethod = SlimeUtils.optionalString(inspector.field(authMethodField))
                                                                     .map(ContainerEndpointSerializer::authMethodFrom)
                                                                     .orElse(ApplicationClusterEndpoint.AuthMethod.mtls);
        if (clusterId.isEmpty()) {
            throw new IllegalStateException("'clusterId' missing on serialized ContainerEndpoint");
        }
        if (scope.isEmpty()) {
            throw new IllegalStateException("'scope' missing on serialized ContainerEndpoint");
        }
        if (!namesInspector.valid()) {
            throw new IllegalStateException("'names' missing on serialized ContainerEndpoint");
        }

        List<String> names = new ArrayList<>();

        namesInspector.traverse((ArrayTraverser) (idx, nameInspector) -> {
            final var containerName = nameInspector.asString();
            names.add(containerName);
        });

        return new ContainerEndpoint(clusterId, scopeFrom(scope), names, weight, routingMethod, authMethod);
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
        cursor.setString(scopeField, asString(endpoint.scope()));
        endpoint.weight().ifPresent(w -> cursor.setLong(weightField, w));
        final var namesInspector = cursor.setArray(namesField);
        endpoint.names().forEach(namesInspector::addString);
        cursor.setString(routingMethodField, asString(endpoint.routingMethod()));
        cursor.setString(authMethodField, asString(endpoint.authMethod()));
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

    private static ApplicationClusterEndpoint.RoutingMethod routingMethodFrom(String s) {
        return switch (s) {
            case "shared" -> ApplicationClusterEndpoint.RoutingMethod.shared;
            case "sharedLayer4" -> ApplicationClusterEndpoint.RoutingMethod.sharedLayer4;
            case "exclusive" -> ApplicationClusterEndpoint.RoutingMethod.exclusive;
            default -> throw new IllegalArgumentException("Unknown routing method '" + s + "'");
        };
    }

    private static ApplicationClusterEndpoint.AuthMethod authMethodFrom(String s) {
        return switch (s) {
            case "mtls" -> ApplicationClusterEndpoint.AuthMethod.mtls;
            case "token" -> ApplicationClusterEndpoint.AuthMethod.token;
            default -> throw new IllegalArgumentException("Unknown auth method '" + s + "'");
        };
    }

    private static ApplicationClusterEndpoint.Scope scopeFrom(String s) {
        return switch (s) {
            case "global" -> ApplicationClusterEndpoint.Scope.global;
            case "application" -> ApplicationClusterEndpoint.Scope.application;
            case "zone" -> ApplicationClusterEndpoint.Scope.zone;
            default -> throw new IllegalArgumentException("Unknown scope '" + s + "'");
        };
    }

    private static String asString(ApplicationClusterEndpoint.RoutingMethod routingMethod) {
        return switch (routingMethod) {
            case shared -> "shared";
            case sharedLayer4 -> "sharedLayer4";
            case exclusive -> "exclusive";
        };
    }

    private static String asString(ApplicationClusterEndpoint.AuthMethod authMethod) {
        return switch (authMethod) {
            case mtls -> "mtls";
            case token -> "token";
        };
    }

    private static String asString(ApplicationClusterEndpoint.Scope scope) {
        return switch (scope) {
            case global -> "global";
            case application -> "application";
            case zone -> "zone";
        };
    }

}
