// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;

import java.time.Duration;

/**
 * @author mpolden
 */
public class HostRenamer extends NodeRepositoryMaintainer {

    private final StringFlag hostnameSchemeFlag;

    public HostRenamer(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostnameSchemeFlag = Flags.HOSTNAME_SCHEME.bindTo(nodeRepository.flagSource());
    }

    @Override
    protected double maintain() {
        NodeList allNodes = nodeRepository().nodes().list();
        if (!NodeMover.zoneIsStable(allNodes)) return 1.0;

        NodeList hosts = allNodes.nodeType(NodeType.host).state(Node.State.active);
        String hostnameScheme = hostnameSchemeFlag.value();
        for (var host : hosts) {
            if (changeHostname(host, hostnameScheme)) {
                nodeRepository().nodes().deprovision(host.hostname(), Agent.system, nodeRepository().clock().instant());
                break;
            }
        }
        return 1.0;
    }

    private boolean changeHostname(Node node, String wantedScheme) {
        return !node.hostname().endsWith(".vespa-cloud.net") && "standard".equals(wantedScheme);
    }

}
