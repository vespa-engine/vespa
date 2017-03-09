package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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

    public NodeRebooter(NodeRepository nodeRepository, Clock clock, Duration rebootInterval) {
        super(nodeRepository, min(Duration.ofMinutes(25), rebootInterval));
        this.rebootInterval = rebootInterval;
        this.clock = clock;
        this.random = new Random(clock.millis()); // seed with clock for test determinism   
    }

    @Override
    protected void maintain() {
        // Reboot candidates: Nodes in long-term states, which we know an safely orchestrate a reboot
        List<Node> rebootCandidates = nodeRepository().getNodes(NodeType.tenant, Node.State.active, Node.State.ready);
        rebootCandidates.addAll(nodeRepository().getNodes(NodeType.proxy, Node.State.active, Node.State.ready));

        for (Node node : rebootCandidates) {
            if (shouldReboot(node))
                nodeRepository().reboot(NodeListFilter.from(node));
        }
    }
    
    private boolean shouldReboot(Node node) {
        Optional<History.Event> lastReboot = node.history().event(History.Event.Type.rebooted);
        if (lastReboot.isPresent() && lastReboot.get().at().plus(rebootInterval).isAfter(clock.instant()))
            return false;
        else // schedule with a probability such that reboots of nodes are spread roughly over the reboot interval
            return random.nextDouble() < (double)rate().getSeconds() / (double)rebootInterval.getSeconds();
    }

    @Override
    public String toString() {
        return "Node rebooter";
    }

}
