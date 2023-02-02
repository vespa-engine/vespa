// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

/**
 * Represents the unique instance id as used in Vespa's integration with Athenz Copper Argos
 *
 * @author bjorncs
 */
public record VespaUniqueInstanceId(int clusterIndex, String clusterId, String instance, String application,
                                    String tenant, String region, String environment, IdentityType type) {


    public static VespaUniqueInstanceId fromDottedString(String instanceId) {
        String[] tokens = instanceId.split("\\.");
        if (tokens.length != 8) {
            throw new IllegalArgumentException("Invalid instance id: " + instanceId);
        }
        return new VespaUniqueInstanceId(
                Integer.parseInt(tokens[0]), tokens[1], tokens[2], tokens[3], tokens[4], tokens[5], tokens[6], IdentityType.fromId(tokens[7]));
    }

    public String asDottedString() {
        return String.format(
                "%d.%s.%s.%s.%s.%s.%s.%s",
                clusterIndex, clusterId, instance, application, tenant, region, environment, type.id());
    }
}
