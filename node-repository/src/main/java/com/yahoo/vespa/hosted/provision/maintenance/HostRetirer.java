// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.HostEvent;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Retire and deprovision hosts that are scheduled for maintenance by the cloud provider.
 *
 * Only applies to dynamically provisioned zones, where a replacement host will be provisioned.
 *
 * @author mpolden
 */
public class HostRetirer extends NodeRepositoryMaintainer {

    private static final Logger LOG = Logger.getLogger(HostRetirer.class.getName());

    private final HostProvisioner hostProvisioner;

    public HostRetirer(NodeRepository nodeRepository, Duration interval, Metric metric, HostProvisioner hostProvisioner) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = Objects.requireNonNull(hostProvisioner);
    }

    @Override
    protected double maintain() {
        if (!nodeRepository().zone().cloud().dynamicProvisioning()) return 1.0;

        NodeList candidates = nodeRepository().nodes().list()
                                              .parents()
                                              .not().deprovisioning();
        List<CloudAccount> cloudAccounts = candidates.stream()
                                                     .map(Node::cloudAccount)
                                                     .filter(cloudAccount -> !cloudAccount.isUnspecified())
                                                     .distinct()
                                                     .collect(Collectors.toList());
        Map<String, List<HostEvent>> eventsByHostId = hostProvisioner.hostEventsIn(cloudAccounts).stream()
                                                                     .collect(Collectors.groupingBy(HostEvent::hostId));
        Instant now = nodeRepository().clock().instant();
        for (var host : candidates) {
            List<HostEvent> events = eventsByHostId.get(host.id());
            if (events == null || events.isEmpty()) continue;

            LOG.info("Deprovisioning " + host + " affected by maintenance event" + (events.size() > 1 ? "s" : "") + ": " + events);
            nodeRepository().nodes().deprovision(host.hostname(), Agent.system, now);
        }
        return 1.0;
    }

}
