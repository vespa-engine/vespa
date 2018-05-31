// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import java.util.Objects;

/**
 * Represents the unique instance id as used in Vespa's integration with Athenz Copper Argos
 *
 * @author bjorncs
 */
public class VespaUniqueInstanceId {

    private final int clusterIndex;
    private final String clusterId;
    private final String instance;
    private final String application;
    private final String tenant;
    private final String region;
    private final String environment;
    private final IdentityType type;

    public VespaUniqueInstanceId(int clusterIndex,
                                 String clusterId,
                                 String instance,
                                 String application,
                                 String tenant,
                                 String region,
                                 String environment,
                                 IdentityType type) {
        this.clusterIndex = clusterIndex;
        this.clusterId = clusterId;
        this.instance = instance;
        this.application = application;
        this.tenant = tenant;
        this.region = region;
        this.environment = environment;
        this.type = type;
    }

    // TODO Remove support for legacy representation without type
    @Deprecated
    public VespaUniqueInstanceId(int clusterIndex,
                                 String clusterId,
                                 String instance,
                                 String application,
                                 String tenant,
                                 String region,
                                 String environment) {
        this(clusterIndex, clusterId, instance, application, tenant, region, environment, null);
    }


    // TODO Remove support for legacy representation without type
    public static VespaUniqueInstanceId fromDottedString(String instanceId) {
        String[] tokens = instanceId.split("\\.");
        if (tokens.length != 7 && tokens.length != 8) {
            throw new IllegalArgumentException("Invalid instance id: " + instanceId);
        }
        return new VespaUniqueInstanceId(
                Integer.parseInt(tokens[0]), tokens[1], tokens[2], tokens[3], tokens[4], tokens[5], tokens[6], tokens.length == 8 ? IdentityType.fromId(tokens[7]) : null);
    }

    // TODO Remove support for legacy representation without type
    public String asDottedString() {
        if (type != null) {
            return String.format(
                    "%d.%s.%s.%s.%s.%s.%s.%s",
                    clusterIndex, clusterId, instance, application, tenant, region, environment, type.id());
        } else {
            return String.format(
                    "%d.%s.%s.%s.%s.%s.%s",
                    clusterIndex, clusterId, instance, application, tenant, region, environment);
        }
    }

    public int clusterIndex() {
        return clusterIndex;
    }

    public String clusterId() {
        return clusterId;
    }

    public String instance() {
        return instance;
    }

    public String application() {
        return application;
    }

    public String tenant() {
        return tenant;
    }

    public String region() {
        return region;
    }

    public String environment() {
        return environment;
    }

    public IdentityType type() { return type; }

    @Override
    public String toString() {
        return "VespaUniqueInstanceId{" +
                "clusterIndex=" + clusterIndex +
                ", clusterId='" + clusterId + '\'' +
                ", instance='" + instance + '\'' +
                ", application='" + application + '\'' +
                ", tenant='" + tenant + '\'' +
                ", region='" + region + '\'' +
                ", environment='" + environment + '\'' +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VespaUniqueInstanceId that = (VespaUniqueInstanceId) o;
        return clusterIndex == that.clusterIndex &&
                Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(instance, that.instance) &&
                Objects.equals(application, that.application) &&
                Objects.equals(tenant, that.tenant) &&
                Objects.equals(region, that.region) &&
                Objects.equals(environment, that.environment) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterIndex, clusterId, instance, application, tenant, region, environment, type);
    }
}
