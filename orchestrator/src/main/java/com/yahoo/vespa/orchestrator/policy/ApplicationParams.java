// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceClusterKey;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-application parameters controlling the orchestration.
 *
 * @author hakonhall
 */
public class ApplicationParams {

    private static final ApplicationParams DEFAULT = new ApplicationParams.Builder().build();

    private final Map<ServiceClusterKey, ClusterParams> clusterParams;

    public static class Builder {
        private final Map<ServiceClusterKey, ClusterParams> clusterParams = new HashMap<>();

        public Builder() {}

        public Builder add(ClusterId clusterId, ServiceType serviceType, ClusterParams clusterParams) {
            this.clusterParams.put(new ServiceClusterKey(clusterId, serviceType), clusterParams);
            return this;
        }

        public ApplicationParams build() {
            return new ApplicationParams(clusterParams);
        }
    }

    public static ApplicationParams getDefault() {
        return DEFAULT;
    }

    private ApplicationParams(Map<ServiceClusterKey, ClusterParams> clusterParams) {
        this.clusterParams = Map.copyOf(clusterParams);
    }

    public ClusterParams clusterParamsFor(ServiceCluster serviceCluster) {
        return clusterParamsFor(serviceCluster.clusterId(), serviceCluster.serviceType());
    }

    public ClusterParams clusterParamsFor(ClusterId clusterId, ServiceType serviceType) {
        var key = new ServiceClusterKey(clusterId, serviceType);
        return clusterParams.getOrDefault(key, ClusterParams.getDefault());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationParams that = (ApplicationParams) o;
        return clusterParams.equals(that.clusterParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterParams);
    }
}
