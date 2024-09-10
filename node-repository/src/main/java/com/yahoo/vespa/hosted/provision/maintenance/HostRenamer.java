// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class HostRenamer extends NodeRepositoryMaintainer {

    private static final Logger LOG = Logger.getLogger(HostRenamer.class.getName());

    private final StringFlag hostnameSchemeFlag;

    public HostRenamer(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostnameSchemeFlag = Flags.HOSTNAME_SCHEME.bindTo(nodeRepository.flagSource());
    }

    @Override
    protected double maintain() {
        if (!nodeRepository().nodes().isWorking()) return 0.0;
        NodeList allNodes = nodeRepository().nodes().list();
        NodeList activeHosts = allNodes.nodeType(NodeType.host).state(Node.State.active);
        Set<ApplicationId> retiringApplications = applicationsOnRetiringHosts(activeHosts, allNodes);
        for (var host : activeHosts) {
            Set<ApplicationId> applicationsOnHost = applicationsOn(host, allNodes);
            if (!changeHostname(host, applicationsOnHost)) continue;

            if (Collections.disjoint(retiringApplications, applicationsOnHost)) {
                LOG.info("Deprovisioning " + host + " to change its hostname");
                nodeRepository().nodes().deprovision(host.hostname(), Agent.system, nodeRepository().clock().instant());
                retiringApplications.addAll(applicationsOnHost);
            }
        }
        return 1.0;
    }

    private Set<ApplicationId> applicationsOn(Node host, NodeList allNodes) {
        Set<ApplicationId> applications = new HashSet<>();
        for (var child : allNodes.childrenOf(host)) {
            applications.add(child.allocation().get().owner());
        }
        return applications;
    }

    private Set<ApplicationId> applicationsOnRetiringHosts(NodeList activeHosts, NodeList allNodes) {
        Set<ApplicationId> applications = new HashSet<>();
        for (var host : activeHosts.retiring()) {
            applications.addAll(applicationsOn(host, allNodes));
        }
        return applications;
    }

    private boolean changeHostname(Node node, Set<ApplicationId> instances) {
        if (node.hostname().endsWith(".vespa-cloud.net")) {
            return false;
        }
        Set<String> wantedSchemes;
        if (instances.isEmpty()) {
            wantedSchemes = Set.of(hostnameSchemeFlag.value());
        } else {
            wantedSchemes = instances.stream()
                                     .map(instance -> hostnameSchemeFlag.withApplicationId(Optional.of(instance)).value())
                                     .collect(Collectors.toSet());
        }
        return wantedSchemes.size() == 1 && wantedSchemes.iterator().next().equals("standard");
    }

}
