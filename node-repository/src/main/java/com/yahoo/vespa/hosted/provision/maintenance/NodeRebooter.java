// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * This schedules periodic reboot of all nodes.
 * We reboot nodes periodically to surface problems at reboot with a smooth frequency rather than
 * potentially in burst when many nodes need to be rebooted for external reasons.
 * 
 * @author bratseth
 */
public class NodeRebooter extends Maintainer {
    
    private final Duration rebootInterval;
    private final Clock clock;
    private final Random random;

    public NodeRebooter(NodeRepository nodeRepository, Clock clock, Duration rebootInterval, JobControl jobControl) {
        super(nodeRepository, min(Duration.ofMinutes(25), rebootInterval), jobControl);
        this.rebootInterval = rebootInterval;
        this.clock = clock;
        this.random = new Random(clock.millis()); // seed with clock for test determinism   
    }

    @Override
    protected void maintain() {
        // Reboot candidates: Nodes in long-term states, which we know can safely orchestrate a reboot
        EnumSet<Node.State> targetStates = EnumSet.of(Node.State.active, Node.State.ready);
        List<Node> nodesToReboot = nodeRepository().getNodes().stream()
                .filter(node -> targetStates.contains(node.state()))
                .filter(node -> node.flavor().getType() != Flavor.Type.DOCKER_CONTAINER)
                .filter(this::shouldReboot)
                .collect(Collectors.toList());

        if (!nodesToReboot.isEmpty())
            nodeRepository().reboot(NodeListFilter.from(nodesToReboot));
    }
    
    private boolean shouldReboot(Node node) {
        if (node.history().hasEventAfter(History.Event.Type.rebooted, clock.instant().minus(rebootInterval)))
            return false;
        else // schedule with a probability such that reboots of nodes are spread roughly over the reboot interval
            return random.nextDouble() < (double) interval().getSeconds() / (double)rebootInterval.getSeconds();
    }

}
