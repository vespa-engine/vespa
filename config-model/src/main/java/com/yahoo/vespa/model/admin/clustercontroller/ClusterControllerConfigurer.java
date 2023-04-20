// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Model serving class. Wraps fleet controller config serving.
 */
public class ClusterControllerConfigurer extends SimpleComponent implements StorDistributionConfig.Producer,
        FleetcontrollerConfig.Producer
{
    private final ContentCluster cluster;
    private final int clusterControllerIndex;
    private final int nodeCount;

    public ClusterControllerConfigurer(ContentCluster cluster, int clusterControllerIndex, int nodeCount) {
        super(new ComponentModel(new BundleInstantiationSpecification(
                new ComponentSpecification("clustercontroller" + "-" + cluster.getName() + "-configurer"),
                new ComponentSpecification("com.yahoo.vespa.clustercontroller.apps.clustercontroller.ClusterControllerClusterConfigurer"),
                new ComponentSpecification("clustercontroller-apps"))));
        this.cluster = cluster;
        this.clusterControllerIndex = clusterControllerIndex;
        this.nodeCount = nodeCount;
    }

    @Override
    public void getConfig(StorDistributionConfig.Builder builder) {
        cluster.getConfig(builder);
    }

    @Override
    public void getConfig(FleetcontrollerConfig.Builder builder) {
        cluster.getConfig(builder);
        cluster.getClusterControllerConfig().getConfig(builder);
        builder.index(clusterControllerIndex);
        builder.fleet_controller_count(nodeCount);
        builder.http_port(0);
        builder.rpc_port(0);
    }

}
