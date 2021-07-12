// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.ClusterId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    private final ListFlag<String> deferApplicationEncryption;

    public HostEncrypter(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.maxEncryptingHosts = Flags.MAX_ENCRYPTING_HOSTS.bindTo(nodeRepository.flagSource());
        this.deferApplicationEncryption = Flags.DEFER_APPLICATION_ENCRYPTION.bindTo(nodeRepository.flagSource());
    }

    @Override
    protected double maintain() {
        Instant now = nodeRepository().clock().instant();
        NodeList allNodes = nodeRepository().nodes().list();
        for (var nodeType : NodeType.values()) {
            if (!nodeType.isHost()) continue;
            if (upgradingVespa(allNodes, nodeType)) continue;
            unencryptedHosts(allNodes, nodeType).forEach(host -> encrypt(host, now));
        }
        return 1.0;
    }

    /** Returns whether any node of given type is currently upgrading its Vespa version */
    private boolean upgradingVespa(NodeList allNodes, NodeType hostType) {
        return allNodes.state(Node.State.ready, Node.State.active)
                       .nodeType(hostType)
                       .changingVersion()
                       .size() > 0;
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

        Set<ApplicationId> deferredApplications = deferApplicationEncryption.value().stream()
                                                                            .map(ApplicationId::fromSerializedForm)
                                                                            .collect(Collectors.toSet());
        NodeList candidates = hostsOfTargetType.state(Node.State.active)
                                               .not().encrypted()
                                               .not().encrypting()
                                               .matching(host -> encryptHost(host, allNodes, deferredApplications))
                                               // Require an OS version supporting encryption
                                               .matching(node -> node.status().osVersion().current()
                                                                     .orElse(Version.emptyVersion)
                                                                     .getMajor() >= 8);

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

    private boolean encryptHost(Node host, NodeList allNodes, Set<ApplicationId> deferredApplications) {
        // TODO: Require a minimum number of proxies in Orchestrator. For now skip proxy hosts.
        if (host.type() == NodeType.proxyhost) return false;

        Set<ApplicationId> applicationsOnHost = allNodes.childrenOf(host).stream()
                                                        .filter(node -> node.allocation().isPresent())
                                                        .map(node -> node.allocation().get().owner())
                                                        .collect(Collectors.toSet());
        return Collections.disjoint(applicationsOnHost, deferredApplications);
    }

    private void encrypt(Node host, Instant now) {
        LOG.info("Retiring and encrypting " + host);
        nodeRepository().nodes().encrypt(host.hostname(), Agent.HostEncrypter, now);
    }

}
