// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.HostService;
import com.yahoo.vespa.serviceview.bindings.ModelResponse;
import com.yahoo.vespa.serviceview.bindings.ServicePort;
import com.yahoo.vespa.serviceview.bindings.ServiceView;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;

/**
 * A transposed view for cloud.config.model.
 *
 * @author Steinar Knutsen
 */
public final class ServiceModel {

    private static final String CONTENT_CLUSTER_TYPENAME = "content";

    private final Map<String, Service> servicesMap;

    /**
     * An ordered list of the clusters in this config model.
     */
    public final ImmutableList<Cluster> clusters;

    ServiceModel(ModelResponse modelConfig) {
        Table<String, String, List<Service>> services = HashBasedTable.create();
        for (HostService h : modelConfig.hosts) {
            String hostName = h.name;
            for (com.yahoo.vespa.serviceview.bindings.Service s : h.services) {
                addService(services, hostName, s);
            }
        }
        List<Cluster> sortingBuffer = new ArrayList<>();
        for (Cell<String, String, List<Service>> c : services.cellSet()) {
            sortingBuffer.add(new Cluster(c.getRowKey(), c.getColumnKey(), c.getValue()));
        }
        Collections.sort(sortingBuffer);
        ImmutableList.Builder<Cluster> clustersBuilder = new ImmutableList.Builder<>();
        clustersBuilder.addAll(sortingBuffer);
        clusters = clustersBuilder.build();
        Map<String, Service> seenIdentifiers = new HashMap<>();
        for (Cluster c : clusters) {
            for (Service s : c.services) {
                List<String> identifiers = s.getIdentifiers();
                for (String identifier : identifiers) {
                    if (seenIdentifiers.containsKey(identifier)) {
                        throw new RuntimeException("Hash collision" + " between " +
                                                   seenIdentifiers.get(identifier) + " and " + s + ".");
                    }
                    seenIdentifiers.put(identifier, s);
                }
            }
        }
        ImmutableMap.Builder<String, Service> servicesBuilder = new ImmutableMap.Builder<>();
        servicesBuilder.putAll(seenIdentifiers);
        servicesMap = servicesBuilder.build();
    }

    private static void addService(Table<String, String, List<Service>> services, String hostName,
                                   com.yahoo.vespa.serviceview.bindings.Service s) {
        boolean hasStateApi = false;
        int statePort = 0;
        List<Integer> ports = new ArrayList<>(s.ports.size());
        for (ServicePort port : s.ports) {
            ports.add(port.number);
            if (!hasStateApi && port.hasTags("http", "state")) {
                hasStateApi = true;
                statePort = port.number;
            }
        }
        // ignore hosts without state API
        if (hasStateApi) {
            Service service = new Service(s.type, hostName, statePort, s.clustername, s.clustertype, s.configid, ports, s.name);
            getAndSetEntry(services, s.clustername, s.clustertype).add(service);
        }
    }

    private static List<Service> getAndSetEntry(Table<String, String, List<Service>> services, String clusterName, String clusterType) {
        List<Service> serviceList = services.get(clusterName, clusterType);
        if (serviceList == null) {
            serviceList = new ArrayList<>();
            services.put(clusterName, clusterType, serviceList);
        }
        return serviceList;
    }

    /**
     * The top level view of a given application.
     *
     * @return a top level view of the entire application in a form suitable for
     *         consumption by a REST API
     */
    public ApplicationView showAllClusters(String uriBase, String applicationIdentifier) {
        ApplicationView response = new ApplicationView();
        List<ClusterView> clusterViews = new ArrayList<>();
        for (Cluster c : clusters) {
            clusterViews.add(showCluster(c, uriBase, applicationIdentifier));
        }
        response.clusters = clusterViews;
        return response;
    }

    private ClusterView showCluster(Cluster c, String uriBase, String applicationIdentifier) {
        List<ServiceView> services = new ArrayList<>();
        for (Service s : c.services) {
            ServiceView service = new ServiceView();
            StringBuilder buffer = getLinkBuilder(uriBase).append(applicationIdentifier).append('/');
            service.url = buffer.append("service/").append(s.getIdentifier(s.statePort)).append("/state/v1/").toString();
            service.serviceType = s.serviceType;
            service.serviceName = s.name;
            service.configId = s.configId;
            service.host = s.host;
            addLegacyLink(uriBase, applicationIdentifier, s, service);
            services.add(service);
        }
        ClusterView v = new ClusterView();
        v.services = services;
        v.name = c.name;
        v.type = c.type;
        if (CONTENT_CLUSTER_TYPENAME.equals(c.type)) {
            Service s = getFirstClusterController();
            StringBuilder buffer = getLinkBuilder(uriBase).append(applicationIdentifier).append('/');
            buffer.append("service/").append(s.getIdentifier(s.statePort)).append("/cluster/v2/").append(c.name);
            v.url = buffer.toString();
        } else {
            v.url = null;
        }
        return v;
    }

    private void addLegacyLink(String uriBase, String applicationIdentifier, Service s, ServiceView service) {
        if (s.serviceType.equals("storagenode") || s.serviceType.equals("distributor")) {
            StringBuilder legacyBuffer = getLinkBuilder(uriBase);
            legacyBuffer.append("legacy/").append(applicationIdentifier).append('/');
            legacyBuffer.append("service/").append(s.getIdentifier(s.statePort)).append('/');
            service.legacyStatusPages = legacyBuffer.toString();
        }
    }

    private Service getFirstServiceInstanceByType(String typeName) {
        for (Cluster c : clusters) {
            for (Service s : c.services) {
                if (typeName.equals(s.serviceType)) {
                    return s;
                }
            }
        }
        throw new IllegalStateException("This installation has but no service of required type: "
                + typeName + ".");
    }

    private Service getFirstClusterController() {
        // This is used assuming all cluster controllers know of all fleet controllers in an application
        return getFirstServiceInstanceByType(CLUSTERCONTROLLER_CONTAINER.serviceName);
    }

    private StringBuilder getLinkBuilder(String uriBase) {
        StringBuilder buffer = new StringBuilder(uriBase);
        if (!uriBase.endsWith("/")) {
            buffer.append('/');
        }
        return buffer;
    }

    @Override
    public String toString() {
        final int maxLen = 3;
        StringBuilder builder = new StringBuilder();
        builder.append("ServiceModel [clusters=")
                .append(clusters.subList(0, Math.min(clusters.size(), maxLen))).append("]");
        return builder.toString();
    }


    /**
     * Match an identifier with a service for this cluster.
     *
     * @param identifier
     *            an opaque service identifier generated by the service
     * @return the corresponding Service instance
     */
    public Service getService(String identifier) {
        return servicesMap.get(identifier);
    }

    /**
     * Find a service based on host and port.
     *
     * @param host
     *            the name of the host running the service
     * @param port
     *            a port owned by the service
     * @param self
     *            the service which generated the host data
     * @return a service instance fullfilling the criteria
     * @throws IllegalArgumentException
     *             if no matching service is found
     */
    public Service resolve(String host, int port, Service self) {
        Integer portAsObject = port;
        String realHost;
        if ("localhost".equals(host)) {
            realHost = self.host;
        } else {
            realHost = host;
        }
        for (Cluster c : clusters) {
            for (Service s : c.services) {
                if (s.host.equals(realHost) && s.ports.contains(portAsObject)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("No registered service owns port " + port + " on host " + realHost + ".");
    }

}
