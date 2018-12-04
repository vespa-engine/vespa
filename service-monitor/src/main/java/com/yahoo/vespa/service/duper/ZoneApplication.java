// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.model.ApplicationInstanceGenerator;

import java.util.Objects;

/**
 * @author hakon
 *
 * TODO: This does not extend InfraApplication because
 * 1) It is not deployed same as the other HostedVespaApplications
 * 2) ZoneApplication has multiple clusters
 */
public class ZoneApplication {

    private ZoneApplication() {}

    private static final ApplicationId ZONE_APPLICATION_ID = InfraApplication
            .createHostedVespaApplicationId("routing");
    private static final ClusterId NODE_ADMIN_CLUSTER_ID = new ClusterId("node-admin");
    private static final ClusterId ROUTING_CLUSTER_ID = new ClusterId("routing");

    public static ApplicationId getApplicationId() {
        return ZONE_APPLICATION_ID;
    }

    public static TenantName getTenantName() {
        return ZONE_APPLICATION_ID.tenant();
    }

    public static ApplicationName getApplicationName() {
        return ZONE_APPLICATION_ID.application();
    }

    public static NodeType getNodeAdminNodeType() {
        return NodeType.host;
    }

    public static ClusterId getNodeAdminClusterId() {
        return NODE_ADMIN_CLUSTER_ID;
    }

    public static ClusterSpec.Type getNodeAdminClusterSpecType() {
        return ClusterSpec.Type.container;
    }

    public static ClusterSpec.Id getNodeAdminClusterSpecId() {
        return new ClusterSpec.Id(getNodeAdminClusterId().s());
    }

    public static ServiceType getNodeAdminServiceType() {
        return ServiceType.CONTAINER;
    }

    public static int getNodeAdminHealthPort() {
        return HostAdminApplication.HOST_ADMIN_HEALT_PORT;
    }

    public static NodeType getRoutingNodeType() {
        return NodeType.proxy;
    }

    public static ClusterId getRoutingClusterId() {
        return ROUTING_CLUSTER_ID;
    }

    public static ClusterSpec.Type getRoutingClusterSpecType() {
        return ClusterSpec.Type.container;
    }

    public static ClusterSpec.Id getRoutingClusterSpecId() {
        return new ClusterSpec.Id(getRoutingClusterId().s());
    }

    public static ServiceType getRoutingServiceType() {
        return ServiceType.CONTAINER;
    }

    public static int getRoutingHealthPort() {
        return 4088;
    }

    public static boolean isNodeAdminService(ApplicationId applicationId,
                                             ClusterId clusterId,
                                             ServiceType serviceType) {
        return Objects.equals(applicationId, getApplicationId()) &&
                Objects.equals(serviceType, getNodeAdminServiceType()) &&
                Objects.equals(clusterId, getNodeAdminClusterId());
    }

    /** Whether a {@link ServiceInfo} belongs to the zone application's node-admin cluster. */
    public static boolean isNodeAdminServiceInfo(ApplicationId applicationId, ServiceInfo serviceInfo) {
        return isNodeAdminService(
                applicationId,
                ApplicationInstanceGenerator.getClusterId(serviceInfo),
                ApplicationInstanceGenerator.toServiceType(serviceInfo));
    }

}
