// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.google.common.base.Joiner;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.admin.Configserver;
import com.yahoo.vespa.model.container.Container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used if clustercontroller is run standalone (not as part of the config server ZooKeeper cluster)
 * to provide common configs to container components.
 *
 * @author Ulf Lilleengen
 */
public class ClusterControllerCluster extends TreeConfigProducer<ClusterControllerContainerCluster> implements
        ZookeeperServerConfig.Producer,
        ZookeepersConfig.Producer {

    private static final int ZK_CLIENT_PORT = 2181; // Must match the default in CuratorConfig
    private ClusterControllerContainerCluster containerCluster = null;
    private final Set<String> previousHosts;

    public ClusterControllerCluster(TreeConfigProducer<?> parent, String subId, DeployState deployState) {
        super(parent, subId);
        this.previousHosts = Collections.unmodifiableSet(deployState.getPreviousModel().stream()
                                                                    .map(Model::allocatedHosts)
                                                                    .map(AllocatedHosts::getHosts)
                                                                    .flatMap(Collection::stream)
                                                                    .map(HostSpec::hostname)
                                                                    .collect(Collectors.toCollection(() -> new LinkedHashSet<>())));
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        builder.clientPort(ZK_CLIENT_PORT);
        builder.juteMaxBuffer(1024 * 1024); // 1 Mb should be more than enough for cluster controller
        builder.snapshotCount(1000);  // Use a low value, few transactions per time unit in cluster controller
        for (ClusterControllerContainer container : containerCluster.getContainers()) {
            ZookeeperServerConfig.Server.Builder serverBuilder = new ZookeeperServerConfig.Server.Builder();
            serverBuilder.hostname(container.getHostName());
            serverBuilder.id(container.index());
            serverBuilder.joining( ! previousHosts.isEmpty() && ! previousHosts.contains(container.getHostName()));
            serverBuilder.retired(container.isRetired());
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
        Objects.requireNonNull(containerCluster);
        for (Container c1 : containerCluster.getContainers()) {
            assert(c1 instanceof ClusterControllerContainer);
            for (Service service : c1.getHostResource().getServices()) {
                if (service instanceof Configserver) {
                    throw new IllegalArgumentException("Error validating cluster controller cluster: cluster controller '" +
                                                       c1.getConfigId() + "' is set to run on the same host as a configserver");
                }
            }
            for (Container c2 : containerCluster.getContainers()) {
                if (c1 != c2 && c1.getHostName().equals(c2.getHostName())) {
                    throw new IllegalArgumentException("Error validating cluster controller cluster: cluster controllers '" +
                                                       c1.getConfigId() + "' and '" + c2.getConfigId() + "' share the same host");
                }
            }
        }
    }

}

