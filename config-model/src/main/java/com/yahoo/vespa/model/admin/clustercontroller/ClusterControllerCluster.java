// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.google.common.base.Joiner;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.admin.Configserver;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Used if clustercontroller is run standalone (not as part of the config server ZooKeeper cluster)
 * to provide common configs to container components.
 *
 * @author Ulf Lilleengen
 * @since 5.6
 */
public class ClusterControllerCluster extends AbstractConfigProducer<ClusterControllerContainerCluster> implements
        ZookeeperServerConfig.Producer,
        ZookeepersConfig.Producer {

    private static final int ZK_CLIENT_PORT = 2181;
    private ClusterControllerContainerCluster containerCluster = null;

    public ClusterControllerCluster(AbstractConfigProducer parent, String subId) {
        super(parent, subId);
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        builder.clientPort(ZK_CLIENT_PORT);
        for (ClusterControllerContainer container : containerCluster.getContainers()) {
            ZookeeperServerConfig.Server.Builder serverBuilder = new ZookeeperServerConfig.Server.Builder();
            serverBuilder.hostname(container.getHostName());
            serverBuilder.id(container.index());
            builder.server(serverBuilder);
        }
    }

    @Override
    public void getConfig(ZookeepersConfig.Builder builder) {
        Collection<String> controllerHosts = new ArrayList<>();
        for (Container container : containerCluster.getContainers()) {
            controllerHosts.add(container.getHostName() + ":" + ZK_CLIENT_PORT);
        }
        builder.zookeeperserverlist(Joiner.on(",").join(controllerHosts));
    }

    @Override
    protected void addChild(ClusterControllerContainerCluster cluster) {
        super.addChild(cluster);
        this.containerCluster = cluster;
    }

    @Override
    public void validate() {
        assert(containerCluster != null);
        for (Container c1 : containerCluster.getContainers()) {
            assert(c1 instanceof ClusterControllerContainer);
            for (Service service : c1.getHostResource().getServices()) {
                if (service instanceof Configserver) {
                    throw new IllegalArgumentException("Error validating cluster controller cluster: cluster controller '" + c1.getConfigId() + "' is set to run on the same host as a configserver");
                }
            }
            for (Container c2 : containerCluster.getContainers()) {
                if (c1 != c2 && c1.getHostName().equals(c2.getHostName())) {
                    throw new IllegalArgumentException("Error validating cluster controller cluster: cluster controllers '" + c1.getConfigId() + "' and '" + c2.getConfigId() + "' share the same host");
                }
            }
        }
    }

}

