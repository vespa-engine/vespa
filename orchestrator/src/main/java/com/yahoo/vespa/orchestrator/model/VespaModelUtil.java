// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yahoo.collections.CollectionUtil.first;

/**
 * Utility methods for working with Vespa-specific model entities (see OrchestratorUtil
 * for more generic model utilities).
 *
 * @author hakonhall
 */
public class VespaModelUtil {
    private static final Logger log = Logger.getLogger(VespaModelUtil.class.getName());

    public static final ApplicationId ZONE_APPLICATION_ID =
            ApplicationId.from("hosted-vespa", "routing", "default");

    public static final ClusterId ADMIN_CLUSTER_ID = new ClusterId("admin");
    public static final ClusterId NODE_ADMIN_CLUSTER_ID = new ClusterId("node-admin");

    public static final ServiceType SLOBROK_SERVICE_TYPE = new ServiceType("slobrok");
    public static final ServiceType CLUSTER_CONTROLLER_SERVICE_TYPE = new ServiceType("container-clustercontroller");
    public static final ServiceType DISTRIBUTOR_SERVICE_TYPE = new ServiceType("distributor");
    public static final ServiceType SEARCHNODE_SERVICE_TYPE = new ServiceType("searchnode");
    public static final ServiceType STORAGENODE_SERVICE_TYPE = new ServiceType("storagenode");

    private static final Comparator<ServiceInstance> CLUSTER_CONTROLLER_INDEX_COMPARATOR =
            Comparator.comparing(serviceInstance -> VespaModelUtil.getClusterControllerIndex(serviceInstance.configId()));

    // @return true iff the service cluster refers to a cluster controller service cluster.
    public static boolean isClusterController(ServiceCluster cluster) {
        return CLUSTER_CONTROLLER_SERVICE_TYPE.equals(cluster.serviceType());
    }

    /**
     * Note that a search node service cluster (service type searchnode aka proton) is
     * always accompanied by a storage node service cluster, but not vice versa.
     *
     * @return true iff the service cluster consists of storage nodes (proton or vds).
     */
    public static boolean isStorage(ServiceCluster cluster) {
        return STORAGENODE_SERVICE_TYPE.equals(cluster.serviceType());
    }

    /**
     * @return true iff the service cluster is a content service cluster.
     */
    public static boolean isContent(ServiceCluster cluster) {
        return DISTRIBUTOR_SERVICE_TYPE.equals(cluster.serviceType()) ||
                SEARCHNODE_SERVICE_TYPE.equals(cluster.serviceType()) ||
                STORAGENODE_SERVICE_TYPE.equals(cluster.serviceType());
    }

    /**
     * @return The set of all Cluster Controller service instances for the application.
     */
    public static List<HostName> getClusterControllerInstancesInOrder(ApplicationInstance application,
                                                                          ClusterId contentClusterId)
    {
        Set<ServiceCluster> controllerClusters = getClusterControllerServiceClusters(application);

        Collection<ServiceCluster> controllerClustersForContentCluster = filter(controllerClusters, contentClusterId);

        Set<ServiceInstance> clusterControllerInstances;
        if (controllerClustersForContentCluster.size() == 1) {
            clusterControllerInstances = first(controllerClustersForContentCluster).serviceInstances();
        } else if (controllerClusters.size() == 1) {
            ServiceCluster cluster = first(controllerClusters);
            log.warning("No cluster controller cluster for content cluster " + contentClusterId
                    + ", using the only cluster controller cluster available: " + cluster.clusterId());

            clusterControllerInstances = cluster.serviceInstances();
        } else {
            throw new RuntimeException("Failed getting cluster controller for content cluster " + contentClusterId +
                    ". Available clusters = " + controllerClusters +
                    ", matching clusters = " + controllerClustersForContentCluster);
        }

        return clusterControllerInstances.stream()
                .sorted(CLUSTER_CONTROLLER_INDEX_COMPARATOR)
                .map(serviceInstance -> serviceInstance.hostName())
                .collect(Collectors.toList());
    }

    private static Collection<ServiceCluster> filter(Set<ServiceCluster> controllerClusters,
                                                            ClusterId contentClusterId) {
        ClusterId clusterControllerClusterId = new ClusterId(contentClusterId.s() + "-controllers");

        return controllerClusters.stream().
                filter(cluster -> cluster.clusterId().equals(clusterControllerClusterId)).
                collect(Collectors.toList());
    }

    public static Set<ServiceCluster> getClusterControllerServiceClusters(ApplicationInstance application) {
        return application.serviceClusters().stream()
                    .filter(VespaModelUtil::isClusterController)
                    .collect(Collectors.toSet());
    }

    /**
     * @return  Host name for a Cluster Controller that is likely to be the master, is !isPresent() if
     *          no cluster controller was found.
     * @throws  java.lang.IllegalArgumentException if there are no cluster controller instances.
     */
    public static HostName getControllerHostName(ApplicationInstance application, ClusterId contentClusterId) {
        //  It happens that the master Cluster Controller is the one with the lowest index, if up.
        return getClusterControllerInstancesInOrder(application, contentClusterId).stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("No cluster controllers found in application " + application));
    }

    /**
     * A content cluster consists of many content-related service clusters, like distributor and storagenode.
     * All of the service clusters within a content cluster have the same service cluster ID,
     * which is also called the content ID (specified in services.xml) and also cluster name
     * (terminology used in Cluster Controller). The cluster name is used when referring to
     * content cluster resources through the HTTP REST on the Cluster Controller.
     *
     * There may be many content clusters within an application. But only one content cluster may be
     * present on any single host,
     *
     * @return The cluster name managed by a Cluster Controller.
     * @throws IllegalArgumentException if there is not exactly one content cluster name.
     */
    public static ClusterId getContentClusterName(ApplicationInstance application, HostName hostName) {
        Set<ClusterId> contentClusterIdsOnHost = application.serviceClusters().stream()
                .filter(VespaModelUtil::isContent)
                .filter(cluster -> clusterHasInstanceOnHost(cluster, hostName))
                .map(ServiceCluster::clusterId)
                .collect(Collectors.toSet());

        if (contentClusterIdsOnHost.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one content cluster within application " +
                    application.applicationInstanceId() + " and host " + hostName + ", but found " +
                    contentClusterIdsOnHost.size() + ": " + contentClusterIdsOnHost + ", application: " +
                    application);
        }

        return contentClusterIdsOnHost.iterator().next();
    }

    private static boolean clusterHasInstanceOnHost(ServiceCluster cluster, HostName hostName) {
        return cluster.serviceInstances().stream().anyMatch(service -> Objects.equals(hostName, service.hostName()));
    }

    /**
     * @return The node index of the storage node running on the host.
     * @throws java.lang.IllegalArgumentException if there is not exactly one storage node running on the host,
     *         or if the index of that storage node could not be found.
     */
    public static int getStorageNodeIndex(ApplicationInstance application, HostName hostName) {
        Optional<ServiceInstance> storageNode = getStorageNodeAtHost(application, hostName);
        if (!storageNode.isPresent()) {
            throw new IllegalArgumentException("Failed to find a storage node for application " +
                    application.applicationInstanceId() + " at host " + hostName);
        }

        return getStorageNodeIndex(storageNode.get().configId());
    }

    public static Optional<ServiceInstance> getStorageNodeAtHost(ApplicationInstance application, 
                                                                        HostName hostName) {
        Set<ServiceInstance> storageNodesOnHost = application.serviceClusters().stream()
                .filter(VespaModelUtil::isStorage)
                .flatMap(cluster -> cluster.serviceInstances().stream())
                .filter(service -> service.hostName().equals(hostName))
                .collect(Collectors.toSet());

        if (storageNodesOnHost.isEmpty()) {
            return Optional.empty();
        }

        if (storageNodesOnHost.size() > 1) {
            throw new RuntimeException("Expected application " + application.applicationInstanceId() +
                    " to have exactly one storage node service on host " + hostName + " but got " +
                    storageNodesOnHost.size() + ": " + storageNodesOnHost);
        }

        return storageNodesOnHost.stream().findAny();
    }

    // See getClusterControllerIndex()
    private static final Pattern CONTROLLER_INDEX_PATTERN = Pattern.compile(".*-controllers/(\\d+)");

    /**
     * @param configId  Must be of the form admin/cluster-controllers/2 or NAME/standalone/NAME-controllers/2,
     *                  where NAME is the name of the content cluster.  NAME-controllers is also the cluster ID of
     *                  the cluster controller cluster.
     * @return the Cluster Controller index given its config ID.
     * @throws java.lang.IllegalArgumentException if the config ID is not of the proper format.
     */
    public static int getClusterControllerIndex(ConfigId configId) {
        Matcher matcher = CONTROLLER_INDEX_PATTERN.matcher(configId.s());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unable to extract cluster controller index from config ID " + configId);
        }

        return Integer.valueOf(matcher.group(1));
    }

    // See getStorageNodeIndex()
    private static final Pattern STORAGE_NODE_INDEX_PATTERN = Pattern.compile(".*/(\\d+)");

    /**
     * @param configId  Config ID is of the form "storage/storage/3", where 3 is the storage node index.
     * @return  The storage node index.
     * @throws java.lang.IllegalArgumentException  If configId does not have the required form.
     */
    public static int getStorageNodeIndex(ConfigId configId) {
        Matcher matcher = STORAGE_NODE_INDEX_PATTERN.matcher(configId.s());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unable to extract node index from config ID " + configId);
        }

        return Integer.valueOf(matcher.group(1));
    }

}
