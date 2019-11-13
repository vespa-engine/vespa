// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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

    private final IntFlag rebootIntervalInDays;
    private final Clock clock;
    private final Random random;

    NodeRebooter(NodeRepository nodeRepository, Clock clock, FlagSource flagSource) {
        super(nodeRepository, Duration.ofMinutes(25));
        this.rebootIntervalInDays = Flags.REBOOT_INTERVAL_IN_DAYS.bindTo(flagSource);
        this.clock = clock;
        this.random = new Random(clock.millis()); // seed with clock for test determinism   
    }

    @Override
    protected void maintain() {
        // Reboot candidates: Nodes in long-term states, which we know can safely orchestrate a reboot
        List<Node> nodesToReboot = nodeRepository().getNodes(Node.State.active, Node.State.ready).stream()
                .filter(node -> node.flavor().getType() != Flavor.Type.DOCKER_CONTAINER)
                .filter(this::shouldReboot)
                .collect(Collectors.toList());

        if (!nodesToReboot.isEmpty())
            nodeRepository().reboot(NodeListFilter.from(nodesToReboot));
    }
    
    private boolean shouldReboot(Node node) {
        if (node.status().reboot().pending()) return false;

        var rebootEvents = EnumSet.of(History.Event.Type.provisioned, History.Event.Type.rebooted, History.Event.Type.osUpgraded);
        var rebootInterval = Duration.ofDays(rebootIntervalInDays.value());

        Optional<Duration> overdue = node.history().events().stream()
                .filter(event -> rebootEvents.contains(event.type()))
                .map(History.Event::at)
                .max(Comparator.naturalOrder())
                .map(lastReboot -> Duration.between(lastReboot, clock.instant()).minus(rebootInterval));

        if (overdue.isEmpty()) // should never happen as all !docker-container should have provisioned timestamp
            return random.nextDouble() < interval().getSeconds() / (double) rebootInterval.getSeconds();

        if (overdue.get().isNegative())
            return false;

        // Use a probability such that each maintain() schedules the same number of reboots,
        // as long as 0 <= overdue <= rebootInterval, with the last maintain() in that interval
        // naturally scheduling the remaining with probability 1.

        int configServers = 3;
        long runs = Math.max(1L, Math.round(rebootInterval.toSeconds() * configServers / (double) interval().toSeconds()));

        double progressFraction = overdue.get().getSeconds() / (double) rebootInterval.getSeconds();
        long currentRun = Math.max(1, Math.min(Math.round(progressFraction * runs), runs));

        double probability = 1.0 / (runs + 1 - currentRun);
        return random.nextDouble() < probability;
    }

}
