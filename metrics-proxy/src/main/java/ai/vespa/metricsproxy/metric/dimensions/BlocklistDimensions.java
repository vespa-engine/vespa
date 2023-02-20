// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.dimensions;

import ai.vespa.metricsproxy.metric.model.DimensionId;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public enum BlocklistDimensions {

    /**
     * Deployment related metrics - most of which are redundant
     * E.g. app/applicationName/tenantName/instanceName is already included in applicationId
     */
    APP("app"),
    APPLICATION_NAME("applicationName"),
    CLUSTER_NAME("clustername"),
    CLUSTER_ID("clusterid"),
    CLUSTER_TYPE("clustertype"),
    DEPLOYMENT_CLUSTER("deploymentCluster"),
    GROUP_ID("groupId"),
    INSTANCE("instance"),
    INSTANCE_NAME("instanceName"),
    TENANT_NAME("tenantName"),

    /**
     * State related dimensions - will always be the same value for a given snapshot
     */
    METRIC_TYPE("metrictype"),
    ORCHESTRATOR_STATE("orchestratorState"),
    ROLE("role"),
    STATE("state"),
    SYSTEM("system"),
    VESPA_VERSION("vespaVersion"),

    /**  Metric specific dimensions  **/
    ARCHITECTURE("arch"),
    AUTHZ_REQUIRED("authz-equired"),
    HOME("home"),
    PORT("port"),
    SCHEME("scheme"),
    DRYRUN("dryrun"),
    VERSION("version");

    private final DimensionId dimensionId;

    BlocklistDimensions(String dimensionId) {
        this.dimensionId = DimensionId.toDimensionId(dimensionId);
    }

    public DimensionId getDimensionId() {
        return dimensionId;
    }

    public static Set<DimensionId> getAll() {
        return EnumSet.allOf(BlocklistDimensions.class)
                .stream()
                .map(BlocklistDimensions::getDimensionId)
                .collect(Collectors.toSet());
    }

}
