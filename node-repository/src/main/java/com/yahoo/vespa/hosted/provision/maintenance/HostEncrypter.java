// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This maintainer triggers encryption of hosts that have unencrypted disk.
 *
 * A host to be encrypted is retired and marked as want-to-encrypt by storing a report.
 *
 * This uses the same host selection criteria as {@link com.yahoo.vespa.hosted.provision.os.RebuildingOsUpgrader}.
 *
 * @author mpolden
 */
// TODO(mpolden): This can be removed once all hosts are encrypted
public class HostEncrypter extends NodeRepositoryMaintainer {

    private static final Logger LOG = Logger.getLogger(HostEncrypter.class.getName());

    private final IntFlag maxEncryptingHosts;

    public HostEncrypter(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.maxEncryptingHosts = Flags.MAX_ENCRYPTING_HOSTS.bindTo(nodeRepository.flagSource());
    }

    @Override
    protected boolean maintain() {
        Instant now = nodeRepository().clock().instant();
        NodeList allNodes = nodeRepository().nodes().list();
        for (var nodeType : NodeType.values()) {
            if (!nodeType.isHost()) continue;
            unencryptedHosts(allNodes, nodeType).forEach(host -> encrypt(host, now));
            triggerRestart(allNodes, nodeType);
        }
        return true;
    }

    /** Returns unencrypted hosts of given type that can be encrypted */
    private List<Node> unencryptedHosts(NodeList allNodes, NodeType hostType) {
        if (!hostType.isHost()) throw new IllegalArgumentException("Expected host type, got " + hostType);
        NodeList hostsOfTargetType =  allNodes.nodeType(hostType);
        int hostLimit = hostLimit(hostsOfTargetType, hostType);

        // Find stateful clusters with retiring nodes
        NodeList activeNodes = allNodes.state(Node.State.active);
        Set<ClusterId> retiringClusters = new HashSet<>(activeNodes.nodeType(hostType.childNodeType())
                                                                   .retiring()
                                                                   .statefulClusters());

        // Encrypt hosts not containing stateful clusters with retiring nodes, up to limit
        List<Node> hostsToEncrypt = new ArrayList<>(hostLimit);
        NodeList candidates = hostsOfTargetType.state(Node.State.active)
                                               .not().encrypted()
                                               .not().encrypting()
                                               // Require an OS version supporting encryption
                                               .matching(node -> node.status().osVersion().current()
                                                                     .orElse(Version.emptyVersion).getMajor() >= 8);

        for (Node host : candidates) {
            if (hostsToEncrypt.size() == hostLimit) break;
            Set<ClusterId> clustersOnHost = activeNodes.childrenOf(host).statefulClusters();
            boolean canEncrypt = Collections.disjoint(retiringClusters, clustersOnHost);
            if (canEncrypt) {
                hostsToEncrypt.add(host);
                retiringClusters.addAll(clustersOnHost);
            }
        }
        return Collections.unmodifiableList(hostsToEncrypt);

    }

    /** Returns the number of hosts that can encrypt concurrently */
    private int hostLimit(NodeList hosts, NodeType hostType) {
        if (hosts.stream().anyMatch(host -> host.type() != hostType)) throw new IllegalArgumentException("All hosts must be a " + hostType);
        if (maxEncryptingHosts.value() < 1) return 0; // 0 or negative value effectively stops encryption of all hosts
        int limit = hostType == NodeType.host ? maxEncryptingHosts.value() : 1;
        return Math.max(0, limit - hosts.encrypting().size());
    }

    /** Trigger restart of encrypting nodes to allow disk encryption to happen */
    private void triggerRestart(NodeList allNodes, NodeType nodeType) {
        NodeList hostsReadyToEncrypt = allNodes.nodeType(nodeType)
                                               .state(Node.State.parked)
                                               .encrypting()
                                               .not().matching(node -> node.allocation().isPresent() &&
                                                                       node.allocation().get().restartGeneration().pending());
        nodeRepository().nodes().restart(NodeListFilter.from(hostsReadyToEncrypt.asList()));
    }

    private void encrypt(Node host, Instant now) {
        LOG.info("Retiring and encrypting " + host);
        nodeRepository().nodes().encrypt(host.hostname(), Agent.HostEncrypter, now);
    }

}
