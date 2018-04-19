// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class ProviderUniqueId {

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

    public ProviderUniqueId(@JsonProperty("tenant") String tenant,
                            @JsonProperty("application") String application,
                            @JsonProperty("environment") String environment,
                            @JsonProperty("region") String region,
                            @JsonProperty("instance") String instance,
                            @JsonProperty("cluster-id") String clusterId,
                            @JsonProperty("cluster-index") int clusterIndex) {
        this.tenant = tenant;
        this.application = application;
        this.environment = environment;
        this.region = region;
        this.instance = instance;
        this.clusterId = clusterId;
        this.clusterIndex = clusterIndex;
    }

    public VespaUniqueInstanceId toVespaUniqueInstanceId() {
        return new VespaUniqueInstanceId(clusterIndex, clusterId, instance, application, tenant, region, environment);
    }

    public static ProviderUniqueId fromVespaUniqueInstanceId(VespaUniqueInstanceId instanceId) {
        return new ProviderUniqueId(
                instanceId.tenant(), instanceId.application(), instanceId.environment(), instanceId.region(),
                instanceId.instance(), instanceId.clusterId(), instanceId.clusterIndex());
    }

    @Override
    public String toString() {
        return "ProviderUniqueId{" +
               "tenant='" + tenant + '\'' +
               ", application='" + application + '\'' +
               ", environment='" + environment + '\'' +
               ", region='" + region + '\'' +
               ", instance='" + instance + '\'' +
               ", clusterId='" + clusterId + '\'' +
               ", clusterIndex=" + clusterIndex +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProviderUniqueId that = (ProviderUniqueId) o;
        return clusterIndex == that.clusterIndex &&
               Objects.equals(tenant, that.tenant) &&
               Objects.equals(application, that.application) &&
               Objects.equals(environment, that.environment) &&
               Objects.equals(region, that.region) &&
               Objects.equals(instance, that.instance) &&
               Objects.equals(clusterId, that.clusterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, application, environment, region, instance, clusterId, clusterIndex);
    }
}
