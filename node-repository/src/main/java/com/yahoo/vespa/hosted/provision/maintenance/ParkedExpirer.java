// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.logging.Logger;

/**
 *
 * Expires parked nodes in dynamically provisioned zones.
 * If number of parked hosts exceed MAX_ALLOWED_PARKED_HOSTS, recycle in a queue order
 *
 * @author olaa
 */
public class ParkedExpirer extends NodeRepositoryMaintainer {

    private static final int MAX_ALLOWED_PARKED_HOSTS = 20;
    private static final Logger log = Logger.getLogger(ParkedExpirer.class.getName());

    private final NodeRepository nodeRepository;

    ParkedExpirer(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.nodeRepository = nodeRepository;
    }

    @Override
    protected double maintain() {
        if (!nodeRepository.zone().getCloud().dynamicProvisioning())
            return 1.0;

        NodeList parkedHosts = nodeRepository.nodes()
                                             .list(Node.State.parked)
                                             .nodeType(NodeType.host)
                                             .not().deprovisioning();
        int hostsToExpire = Math.max(0, parkedHosts.size() - MAX_ALLOWED_PARKED_HOSTS);
        parkedHosts.sortedBy(Comparator.comparing(this::parkedAt))
                   .first(hostsToExpire)
                   .forEach(host -> {
                       log.info("Allowed number of parked nodes exceeded. Recycling " + host.hostname());
                       nodeRepository.nodes().deallocate(host, Agent.ParkedExpirer, "Expired by ParkedExpirer");
                   });

        return 1.0;
    }

    private Instant parkedAt(Node node) {
        return node.history().event(History.Event.Type.parked)
                   .map(History.Event::at)
                   .orElse(Instant.EPOCH); // Should not happen
    }

}
