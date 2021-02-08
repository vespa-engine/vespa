// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;

/**
 * This maintainer (de)activates OS upgrades according to Vespa upgrade status of nodes in this repository.
 *
 * If a node is upgrading to a new Vespa version, any ongoing OS upgrade will be paused for all nodes of that type. OS
 * upgrades will resume once all nodes of that type have completed their Vespa upgrade.
 *
 * @author mpolden
 */
public class OsUpgradeActivator extends NodeRepositoryMaintainer {

    public OsUpgradeActivator(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
    }

    @Override
    protected boolean maintain() {
        for (var nodeType : NodeType.values()) {
            if (!nodeType.isHost()) continue;
            boolean resume = canUpgradeOsOf(nodeType);
            nodeRepository().osVersions().resumeUpgradeOf(nodeType, resume);
        }
        return true;
    }

    /** Returns whether to allow OS upgrade of nodes of given type */
    private boolean canUpgradeOsOf(NodeType type) {
        return nodeRepository().nodes()
                               .list(Node.State.ready, Node.State.active)
                               .nodeType(type)
                               .changingVersion()
                               .asList()
                               .isEmpty();
    }

}
