// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class VespaUniqueInstanceIdEntity {

    @JsonProperty("tenant")
    public final String tenant;
    @JsonProperty("application")
    public final String application;
    @JsonProperty("environment")
    public final String environment;
    @JsonProperty("region")
    public final String region;
    @JsonProperty("instance")
    public final String instance;
    @JsonProperty("cluster-id")
    public final String clusterId;
    @JsonProperty("cluster-index")
    public final int clusterIndex;
    @JsonProperty("type")
    public final String type;

    @JsonCreator
    public VespaUniqueInstanceIdEntity(@JsonProperty("tenant") String tenant,
                                       @JsonProperty("application") String application,
                                       @JsonProperty("environment") String environment,
                                       @JsonProperty("region") String region,
                                       @JsonProperty("instance") String instance,
                                       @JsonProperty("cluster-id") String clusterId,
                                       @JsonProperty("cluster-index") int clusterIndex,
                                       @JsonProperty("type") String type) {
        this.tenant = tenant;
        this.application = application;
        this.environment = environment;
        this.region = region;
        this.instance = instance;
        this.clusterId = clusterId;
        this.clusterIndex = clusterIndex;
        this.type = type;
    }

    @Deprecated
    public VespaUniqueInstanceIdEntity(String tenant,
                                       String application,
                                       String environment,
                                       String region,
                                       String instance,
                                       String clusterId,
                                       int clusterIndex) {
        this(tenant, application, environment, region, instance, clusterId, clusterIndex, null);
    }


    @Override
    public String toString() {
        return "VespaUniqueInstanceIdEntity{" +
                "tenant='" + tenant + '\'' +
                ", application='" + application + '\'' +
                ", environment='" + environment + '\'' +
                ", region='" + region + '\'' +
                ", instance='" + instance + '\'' +
                ", clusterId='" + clusterId + '\'' +
                ", clusterIndex=" + clusterIndex +
                ", type='" + type + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VespaUniqueInstanceIdEntity that = (VespaUniqueInstanceIdEntity) o;
        return clusterIndex == that.clusterIndex &&
                Objects.equals(tenant, that.tenant) &&
                Objects.equals(application, that.application) &&
                Objects.equals(environment, that.environment) &&
                Objects.equals(region, that.region) &&
                Objects.equals(instance, that.instance) &&
                Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, application, environment, region, instance, clusterId, clusterIndex, type);
    }
}
