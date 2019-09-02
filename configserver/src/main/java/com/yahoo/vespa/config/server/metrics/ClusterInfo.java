// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.ServiceInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author olaa
 */
public class ClusterInfo {

    private static final Set<String> CONTENT_SERVICES = Set.of("storagenode", "searchnode", "distributor", "container-clustercontroller");
    private static final Set<String> CONTAINER_SERVICES = Set.of("qrserver", "container");

    private final String clusterId;
    private final ClusterType clusterType;
    private final List<URI> hostnames;

    public ClusterInfo(String clusterId, ClusterType clusterType) {
        this(clusterId, clusterType, new ArrayList<>());
    }

    public ClusterInfo(String clusterId, ClusterType clusterType, List<URI> hostnames) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.hostnames = hostnames;
    }

    public String getClusterId() {
        return clusterId;
    }

    public ClusterType getClusterType() {
        return clusterType;
    }

    public List<URI> getHostnames() {
        return hostnames;
    }

    public void addHost(URI host) {
        hostnames.add(host);
    }

    // Try to determine whether host is content or container based on service
    public static Optional<ClusterInfo> fromServiceInfo(ServiceInfo serviceInfo) {
        String serviceType = serviceInfo.getServiceType();
        ClusterType clusterType;
        if (CONTENT_SERVICES.contains(serviceType)) clusterType = ClusterType.content;
        else if (CONTAINER_SERVICES.contains(serviceType)) clusterType = ClusterType.container;
        else return Optional.empty();
        return Optional.of(new ClusterInfo(serviceInfo.getServiceName(), clusterType));
    }

    public enum ClusterType {
        content,
        container
    }
}
