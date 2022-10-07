// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 */
public class DimensionHelper {

    private static final Map<FetchVector.Dimension, String> serializedDimensions = new HashMap<>();

    static {
        serializedDimensions.put(FetchVector.Dimension.ZONE_ID, "zone");
        serializedDimensions.put(FetchVector.Dimension.HOSTNAME, "hostname");
        serializedDimensions.put(FetchVector.Dimension.APPLICATION_ID, "application");
        serializedDimensions.put(FetchVector.Dimension.NODE_TYPE, "node-type");
        serializedDimensions.put(FetchVector.Dimension.CLUSTER_ID, "cluster-id");
        serializedDimensions.put(FetchVector.Dimension.CLUSTER_TYPE, "cluster-type");
        serializedDimensions.put(FetchVector.Dimension.VESPA_VERSION, "vespa-version");
        serializedDimensions.put(FetchVector.Dimension.CONSOLE_USER_EMAIL, "console-user-email");
        serializedDimensions.put(FetchVector.Dimension.TENANT_ID, "tenant");

        if (serializedDimensions.size() != FetchVector.Dimension.values().length) {
            throw new IllegalStateException(FetchVectorHelper.class.getName() + " is not in sync with " +
                    FetchVector.Dimension.class.getName());
        }
    }

    private static final Map<String, FetchVector.Dimension> deserializedDimensions = serializedDimensions.
            entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    public static String toWire(FetchVector.Dimension dimension) {
        String serializedDimension = serializedDimensions.get(dimension);
        if (serializedDimension == null) {
            throw new IllegalArgumentException("Unsupported dimension (please add it): '" + dimension + "'");
        }

        return serializedDimension;
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
