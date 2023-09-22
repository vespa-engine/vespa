// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hakonhall
 */
public class DimensionHelper {

    private static final Map<FetchVector.Dimension, List<String>> serializedDimensions = new HashMap<>();

    static {
        serializedDimensions.put(FetchVector.Dimension.CLOUD, List.of("cloud"));
        serializedDimensions.put(FetchVector.Dimension.CLOUD_ACCOUNT, List.of("cloud-account"));
        serializedDimensions.put(FetchVector.Dimension.CLUSTER_ID, List.of("cluster-id"));
        serializedDimensions.put(FetchVector.Dimension.CLUSTER_TYPE, List.of("cluster-type"));
        serializedDimensions.put(FetchVector.Dimension.CONSOLE_USER_EMAIL, List.of("console-user-email"));
        serializedDimensions.put(FetchVector.Dimension.ENVIRONMENT, List.of("environment"));
        serializedDimensions.put(FetchVector.Dimension.HOSTNAME, List.of("hostname"));
        serializedDimensions.put(FetchVector.Dimension.INSTANCE_ID, List.of("application", "instance"));
        serializedDimensions.put(FetchVector.Dimension.NODE_TYPE, List.of("node-type"));
        serializedDimensions.put(FetchVector.Dimension.SYSTEM, List.of("system"));
        serializedDimensions.put(FetchVector.Dimension.TENANT_ID, List.of("tenant"));
        serializedDimensions.put(FetchVector.Dimension.VESPA_VERSION, List.of("vespa-version"));
        serializedDimensions.put(FetchVector.Dimension.ZONE_ID, List.of("zone"));

        if (serializedDimensions.size() != FetchVector.Dimension.values().length) {
            throw new IllegalStateException(FetchVectorHelper.class.getName() + " is not in sync with " +
                    FetchVector.Dimension.class.getName());
        }
    }

    private static final Map<String, FetchVector.Dimension> deserializedDimensions = reverseMapping(serializedDimensions);

    private static Map<String, FetchVector.Dimension> reverseMapping(Map<FetchVector.Dimension, List<String>> mapping) {
        Map<String, FetchVector.Dimension> reverseMapping = new LinkedHashMap<>();
        mapping.forEach((dimension, serializedDimensions) -> {
            serializedDimensions.forEach(serializedDimension -> {
                if (reverseMapping.put(serializedDimension, dimension) != null) {
                    throw new IllegalStateException("Duplicate serialized dimension: '" + serializedDimension + "'");
                }
            });
        });
        return Map.copyOf(reverseMapping);
    }

    public static String toWire(FetchVector.Dimension dimension) {
        List<String> serializedDimension = serializedDimensions.get(dimension);
        if (serializedDimension == null || serializedDimension.isEmpty()) {
            throw new IllegalArgumentException("Unsupported dimension (please add it): '" + dimension + "'");
        }

        return serializedDimension.get(0);
    }

    public static FetchVector.Dimension fromWire(String serializedDimension) {
        FetchVector.Dimension dimension = deserializedDimensions.get(serializedDimension);
        if (dimension == null) {
            throw new IllegalArgumentException("Unknown serialized dimension: '" + serializedDimension + "'");
        }

        return dimension;
    }

    private DimensionHelper() { }

}
