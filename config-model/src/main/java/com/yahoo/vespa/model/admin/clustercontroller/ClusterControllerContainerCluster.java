package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerClusterVerifier;

/**
 * Container cluster for cluster-controller containers.
 *
 * @author gjoranv
 */
public class ClusterControllerContainerCluster extends ContainerCluster<ClusterControllerContainer> implements
        ThreadpoolConfig.Producer
{

    public ClusterControllerContainerCluster(AbstractConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState);
    }

    public ClusterControllerContainerCluster(AbstractConfigProducer<?> parent, String subId, String name, ContainerClusterVerifier verifier, DeployState deployState) {
        super(parent, subId, name, verifier, deployState);
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        builder.maxthreads(10);
    }

    @Override
    protected void myPrepare(DeployState deployState) { }

}
