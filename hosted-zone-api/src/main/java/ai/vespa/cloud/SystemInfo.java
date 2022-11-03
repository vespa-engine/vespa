// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.Objects;

/**
 * Provides information about the system in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 *
 * @author bratseth
 */
public class SystemInfo {

    private final ApplicationId application;
    private final Zone zone;
    private final Cloud cloud;
    private final String clusterName;
    private final Node node;

    // TODO: Remove on Vespa 9
    @Deprecated(forRemoval = true)
    public SystemInfo(ApplicationId application, Zone zone, Cluster cluster, Node node) {
        this(application, zone, new Cloud(""), cluster.id(), node);
    }
    @Deprecated(forRemoval = true)
    public SystemInfo(ApplicationId application, Zone zone, Cloud cloud, Cluster cluster, Node node) {
        this(application, zone, cloud, cluster.id(), node);
    }

    public SystemInfo(ApplicationId application, Zone zone, Cloud cloud, String clusterName, Node node) {
        this.application = Objects.requireNonNull(application, "Application cannot be null");
        this.zone = Objects.requireNonNull(zone, "Zone cannot be null");
        this.cloud = Objects.requireNonNull(cloud, "Cloud cannot be null");
        this.clusterName = Objects.requireNonNull(clusterName, "ClusterName cannot be null");
        this.node = Objects.requireNonNull(node, "Node cannot be null");
    }

    /** Returns the application this is running as a part of */
    public ApplicationId application() { return application; }

    /** Returns the zone this is running in */
    public Zone zone() { return zone; }

    /** Returns the cloud provider this is running in */
    public Cloud cloud() {
        return cloud;
    }

    /** Returns the name of the cluster it is running in */
    public String clusterName() { return clusterName; }

    /** Returns the node this is running on */
    public Node node() { return node; }

}
