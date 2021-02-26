// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.zookeeper.Reconfigurer;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reconfigure members of the config server ZooKeeper cluster, according to the config servers currently active in the
 * node repository.
 *
 * @author mpolden
 */
public class ConfigServerReconfigurer extends NodeRepositoryMaintainer {

    /** Minimum number of config servers required before attempting reconfiguration */
    private static final int MIN_ACTIVE_NODES = 3;

    private final Reconfigurer reconfigurer;
    private final BooleanFlag featureFlag;

    public ConfigServerReconfigurer(NodeRepository nodeRepository, Duration interval, Metric metric, Reconfigurer reconfigurer) {
        super(nodeRepository, interval, metric);
        this.reconfigurer = reconfigurer;
        this.featureFlag = Flags.DYNAMIC_CONFIG_SERVER_PROVISIONING.bindTo(nodeRepository.flagSource());
    }

    @Override
    protected boolean maintain() {
        if (!nodeRepository().zone().getCloud().dynamicProvisioning()) return true;
        if (!featureFlag.value()) return true;

        NodeList configNodes = nodeRepository().nodes().list(Node.State.active)
                                               .nodeType(NodeType.config);
        if (configNodes.size() < MIN_ACTIVE_NODES) return true;
        List<ZooKeeperServer> servers = configNodes.stream()
                                                   .map(node -> new ZooKeeperServer(node.allocation().get().membership().index(), node.hostname()))
                                                   .collect(Collectors.toList());
        reconfigurer.reconfigure(servers);
        return true;
    }

}
