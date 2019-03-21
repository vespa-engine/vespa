package com.yahoo.vespa.model.admin.metricsproxy;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.ContainerCluster;

/**
 * Container cluster for metrics proxy containers.
 *
 * @author gjoranv
 */
public class MetricsProxyContainerCluster extends ContainerCluster<MetricsProxyContainer> {

    public MetricsProxyContainerCluster(AbstractConfigProducer<?> parent, String name, DeployState deployState) {
        super(parent, name, name, deployState);
        setRpcServerEnabled(false);
        addDefaultHandlersExceptStatus();
    }

    @Override
    protected void doPrepare(DeployState deployState) { }

}
