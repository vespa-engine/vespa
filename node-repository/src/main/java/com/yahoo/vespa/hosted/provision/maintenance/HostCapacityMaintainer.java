// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisionRequest;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;
import com.yahoo.vespa.hosted.provision.provisioning.NodeCandidate;
import com.yahoo.vespa.hosted.provision.provisioning.NodePrioritizer;
import com.yahoo.vespa.hosted.provision.provisioning.NodeSpec;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

/**
 * @author freva
 * @author mpolden
 */
public class HostCapacityMaintainer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(HostCapacityMaintainer.class.getName());

    private final HostProvisioner hostProvisioner;
    private final ListFlag<ClusterCapacity> preprovisionCapacityFlag;

    HostCapacityMaintainer(NodeRepository nodeRepository,
                           Duration interval,
                           HostProvisioner hostProvisioner,
                           FlagSource flagSource,
                           Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = hostProvisioner;
        this.preprovisionCapacityFlag = PermanentFlags.PREPROVISION_CAPACITY.bindTo(flagSource);
    }

    @Override
    protected double maintain() {
        List<Node> provisionedSnapshot;
        try {
            NodeList nodes;
            // Host and child nodes are written in separate transactions, but both are written while holding the
            // unallocated lock. Hold the unallocated lock while reading nodes to ensure we get all the children
            // of newly provisioned hosts.
            try (Mutex ignored = nodeRepository().nodes().lockUnallocated()) {
                nodes = nodeRepository().nodes().list();
            }
            provisionedSnapshot = provision(nodes);
        } catch (NodeAllocationException | IllegalStateException e) {
            log.log(Level.WARNING, "Failed to allocate preprovisioned capacity and/or find excess hosts: " + e.getMessage());
            return 0;  // avoid removing excess hosts
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to allocate preprovisioned capacity and/or find excess hosts", e);
            return 0;  // avoid removing excess hosts
        }

        return markForRemoval(provisionedSnapshot);
    }

    private double markForRemoval(List<Node> provisionedSnapshot) {
        List<Node> emptyHosts = findEmptyOrRemovableHosts(provisionedSnapshot);
        if (emptyHosts.isEmpty()) return 1;

        int attempts = 0, success = 0;
        for (Set<Node> typeEmptyHosts : emptyHosts.stream().collect(groupingBy(Node::type, toSet())).values()) {
            attempts++;
            // All nodes in the list are hosts of the same type, so they use the same lock regardless of their allocation
            Optional<NodeMutex> appMutex = nodeRepository().nodes().lockAndGet(typeEmptyHosts.iterator().next(), Duration.ofSeconds(10));
            if (appMutex.isEmpty()) continue;
            try (Mutex lock = appMutex.get();
                 Mutex unallocatedLock = nodeRepository().nodes().lockUnallocated()) {
                // Re-read all nodes under lock and compute the candidates for removal. The actual nodes we want
                // to mark for removal is the intersection with typeEmptyHosts, which excludes the preprovisioned hosts.
                Map<Optional<String>, List<Node>> currentNodesByParent = nodeRepository().nodes().list().stream().collect(groupingBy(Node::parentHostname));
                List<Node> candidateHosts = new ArrayList<>(getHosts(currentNodesByParent));
                candidateHosts.retainAll(typeEmptyHosts);

                for (Node host : candidateHosts) {
                    attempts++;

                    // Any hosts that are no longer empty should be marked as such, and excluded from removal.
                    if (currentNodesByParent.getOrDefault(Optional.of(host.hostname()), List.of()).stream().anyMatch(n -> ! canDeprovision(n))
                                    && host.hostEmptyAt().isPresent()) {
                        nodeRepository().nodes().write(host.withHostEmptyAt(null), lock);
                    }
                    // If the host is still empty, we can mark it as empty now, or mark it for removal if it has already expired.
                    else {
                        Instant now = clock().instant();
                        Node emptyHost = host.hostEmptyAt().isPresent() ? host : host.withHostEmptyAt(now);
                        boolean expired = ! now.isBefore(emptyHost.hostEmptyAt().get().plus(host.hostTTL().orElse(Duration.ZERO)));

                        if (expired && canRemoveHost(emptyHost)) {
                            // Retire the host to parked if possible, otherwise move it straight to parked.
                            if (EnumSet.of(Node.State.reserved, Node.State.active, Node.State.inactive).contains(host.state())) {
                                emptyHost = emptyHost.withWantToRetire(true, true, Agent.HostCapacityMaintainer, now);
                                nodeRepository().nodes().write(emptyHost, lock);
                            }
                            else {
                                if (emptyHost != host) nodeRepository().nodes().write(emptyHost, lock);
                                nodeRepository().nodes().park(host.hostname(), true, Agent.HostCapacityMaintainer, "Parked for removal");
                            }
                        }
                        else {
                            if (emptyHost != host) nodeRepository().nodes().write(emptyHost, lock);
                        }
                    }

                    success++;
                }
            } catch (UncheckedTimeoutException e) {
                log.log(Level.WARNING, "Failed to mark excess hosts for deprovisioning: Failed to get lock, will retry later");
            }
            success++;
        }
        return asSuccessFactorDeviation(attempts, attempts - success);
    }

    private List<Node> provision(NodeList nodeList) {
        return provisionUntilNoDeficit(nodeList).stream()
                                                .sorted(comparing(node -> node.history().events().stream()
                                                                              .map(History.Event::at)
                                                                              .min(naturalOrder())
                                                                              .orElse(Instant.MIN)))
                                                .toList();
    }

    private static boolean canRemoveHost(Node host) {
        return switch (host.type()) {
            // TODO: Mark empty tenant hosts as wanttoretire & wanttodeprovision elsewhere, then handle as confighost here
            case host -> host.state() != Node.State.deprovisioned &&
                         (host.state() != Node.State.parked || host.status().wantToDeprovision());
            case confighost, controllerhost -> canDeprovision(host);
            default -> false;
        };
    }

    static boolean canDeprovision(Node node) {
        return node.status().wantToDeprovision() && (node.state() == Node.State.parked ||
                                                     node.state() == Node.State.failed);
    }

    /**
     * @return the nodes in {@code nodeList} plus all hosts provisioned, plus all preprovision capacity
     *         nodes that were allocated.
     * @throws NodeAllocationException if there were problems provisioning hosts, and in case message
     *         should be sufficient (avoid no stack trace)
     * @throws IllegalStateException if there was an algorithmic problem, and in case message
     *         should be sufficient (avoid no stack trace).
     */
    private List<Node> provisionUntilNoDeficit(NodeList nodeList) {
        List<ClusterCapacity> preprovisionCapacity = preprovisionCapacityFlag.value();

        // Worst-case each ClusterCapacity in preprovisionCapacity will require an allocation.
        int maxProvisions = preprovisionCapacity.size();

        var nodesPlusProvisioned = new ArrayList<>(nodeList.asList());
        for (int numProvisions = 0;; ++numProvisions) {
            var nodesPlusProvisionedPlusAllocated = new ArrayList<>(nodesPlusProvisioned);
            Optional<ClusterCapacity> deficit = allocatePreprovisionCapacity(preprovisionCapacity, nodesPlusProvisionedPlusAllocated);
            if (deficit.isEmpty()) {
                return nodesPlusProvisionedPlusAllocated;
            }

            if (numProvisions >= maxProvisions) {
                throw new IllegalStateException("Have provisioned " + numProvisions + " times but there's still deficit: aborting");
            }

            nodesPlusProvisioned.addAll(provisionHosts(deficit.get().count(), toNodeResources(deficit.get())));
        }
    }

    private List<Node> provisionHosts(int count, NodeResources nodeResources) {
        try {
            Version osVersion = nodeRepository().osVersions().targetFor(NodeType.host).orElse(Version.emptyVersion);
            List<Integer> provisionIndices = nodeRepository().database().readProvisionIndices(count);
            List<Node> hosts = new ArrayList<>();
            HostProvisionRequest request = new HostProvisionRequest(provisionIndices, NodeType.host, nodeResources, ApplicationId.defaultId(), osVersion,
                                                                    HostSharing.shared, Optional.empty(), Optional.empty(),
                                                                    nodeRepository().zone().cloud().account(), false);
            hostProvisioner.provisionHosts(request,
                                           provisionedHosts -> {
                                               hosts.addAll(provisionedHosts.stream().map(host -> host.generateHost(Duration.ZERO)).toList());
                                               nodeRepository().nodes().addNodes(hosts, Agent.HostCapacityMaintainer);
                                           });
            return hosts;
        } catch (NodeAllocationException | IllegalArgumentException | IllegalStateException e) {
            throw new NodeAllocationException("Failed to provision " + count + " " + nodeResources + ": " + e.getMessage(),
                                              ! (e instanceof NodeAllocationException nae) || nae.retryable());
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to provision " + count + " " + nodeResources + ", will retry in " + interval(), e);
        }
    }

    /**
     * Try to allocate the preprovision cluster capacity.
     *
     * @param mutableNodes represents all nodes in the node repo.  As preprovision capacity is virtually allocated
     *                     they are added to {@code mutableNodes}
     * @return the part of a cluster capacity it was unable to allocate, if any
     */
    private Optional<ClusterCapacity> allocatePreprovisionCapacity(List<ClusterCapacity> preprovisionCapacity,
                                                                   ArrayList<Node> mutableNodes) {
        for (int clusterIndex = 0; clusterIndex < preprovisionCapacity.size(); ++clusterIndex) {
            ClusterCapacity clusterCapacity = preprovisionCapacity.get(clusterIndex);
            LockedNodeList allNodes = new LockedNodeList(mutableNodes, () -> {});
            List<Node> candidates = findCandidates(clusterCapacity, clusterIndex, allNodes);
            int deficit = Math.max(0, clusterCapacity.count() - candidates.size());
            if (deficit > 0) {
                return Optional.of(clusterCapacity.withCount(deficit));
            }

            // Simulate allocating the cluster
            mutableNodes.addAll(candidates);
        }

        return Optional.empty();
    }

    private List<Node> findCandidates(ClusterCapacity clusterCapacity, int clusterIndex, LockedNodeList allNodes) {
        NodeResources nodeResources = toNodeResources(clusterCapacity);

        // We'll allocate each ClusterCapacity as a unique cluster in a dummy application
        ApplicationId applicationId = ApplicationId.defaultId();
        ClusterSpec.Id clusterId = ClusterSpec.Id.from(String.valueOf(clusterIndex));
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.content, clusterId)
                // build() requires a version, even though it is not (should not be) used
                .vespaVersion(Vtag.currentVersion)
                .build();
        NodeSpec nodeSpec = NodeSpec.from(clusterCapacity.count(), nodeResources, false, true,
                                          nodeRepository().zone().cloud().account(), Duration.ZERO);
        int wantedGroups = 1;

        NodePrioritizer prioritizer = new NodePrioritizer(allNodes, applicationId, clusterSpec, nodeSpec, wantedGroups,
                true, nodeRepository().nameResolver(), nodeRepository().nodes(), nodeRepository().resourcesCalculator(),
                nodeRepository().spareCount(), nodeSpec.cloudAccount().isExclave(nodeRepository().zone()));
        List<NodeCandidate> nodeCandidates = prioritizer.collect(List.of());
        MutableInteger index = new MutableInteger(0);
        return nodeCandidates
                .stream()
                .limit(clusterCapacity.count())
                .map(candidate -> candidate.toNode()
                        .allocate(applicationId,
                                  ClusterMembership.from(clusterSpec, index.next()),
                                  nodeResources,
                                  nodeRepository().clock().instant()))
                .toList();
    }

    private static NodeResources toNodeResources(ClusterCapacity clusterCapacity) {
        return new NodeResources(clusterCapacity.vcpu(),
                                 clusterCapacity.memoryGb(),
                                 clusterCapacity.diskGb(),
                                 clusterCapacity.bandwidthGbps(),
                                 NodeResources.DiskSpeed.valueOf(clusterCapacity.diskSpeed()),
                                 NodeResources.StorageType.valueOf(clusterCapacity.storageType()),
                                 NodeResources.Architecture.valueOf(clusterCapacity.architecture()));
    }

    private static List<Node> findEmptyOrRemovableHosts(List<Node> provisionedSnapshot) {
        // Group nodes by parent; no parent means it's a host.
        var nodesByParent = provisionedSnapshot.stream().collect(groupingBy(Node::parentHostname));

        // Find all hosts that we once thought were empty (first clause), or whose children are now all removable (second clause).
        return getHosts(nodesByParent).stream()
                .filter(host -> host.hostEmptyAt().isPresent() || allChildrenCanBeDeprovisioned(nodesByParent, host))
                .toList();
    }

    private static List<Node> getHosts(Map<Optional<String>, List<Node>> nodesByParent) {
        return nodesByParent.get(Optional.<String>empty());
    }

    private static List<Node> getChildren(Map<Optional<String>, List<Node>> nodesByParent, Node host) {
        return nodesByParent.getOrDefault(Optional.of(host.hostname()), List.of());
    }

    private static boolean allChildrenCanBeDeprovisioned(Map<Optional<String>, List<Node>> nodesByParent, Node host) {
        return getChildren(nodesByParent, host).stream().allMatch(HostCapacityMaintainer::canDeprovision);
    }

}
