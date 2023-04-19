// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceComparator;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author mgimle
 */
public class CapacityChecker {

    // We only care about nodes in one of these states.
    private static final Set<Node.State> relevantNodeStates = EnumSet.of(
            Node.State.active, Node.State.inactive, Node.State.provisioned, Node.State.ready, Node.State.reserved);

    private final List<Node> hosts;
    private final Map<String, Node> nodeMap;
    private final Map<Node, List<Node>> nodeChildren;
    private final Map<Node, AllocationResources> availableResources;

    public AllocationHistory allocationHistory = null;

    public CapacityChecker(NodeList allNodes) {
        this.hosts = allNodes.hosts().state(relevantNodeStates).asList();
        List<Node> tenants = getTenants(allNodes, hosts);
        nodeMap = constructHostnameToNodeMap(hosts);
        this.nodeChildren = constructNodeChildrenMap(tenants, hosts, nodeMap);
        this.availableResources = constructAvailableResourcesMap(hosts, nodeChildren);
    }

    public List<Node> getHosts() {
        return hosts;
    }

    public Optional<HostFailurePath> worstCaseHostLossLeadingToFailure() {
        Map<Node, Integer> timesNodeCanBeRemoved = computeMaximalRepeatedRemovals();
        return greedyHeuristicFindFailurePath(timesNodeCanBeRemoved);
    }

    protected List<Node> findOvercommittedHosts() {
        List<Node> overcommittedNodes = new ArrayList<>();
        for (var entry : availableResources.entrySet()) {
            var resources = entry.getValue().nodeResources;
            if (resources.vcpu() < 0 || resources.memoryGb() < 0 || resources.diskGb() < 0) {
                overcommittedNodes.add(entry.getKey());
            }
        }
        return overcommittedNodes;
    }

    public List<Node> nodesFromHostnames(List<String> hostnames) {
        return hostnames.stream().filter(nodeMap::containsKey)
                                    .map(nodeMap::get)
                                    .toList();

    }

    public Optional<HostFailurePath> findHostRemovalFailure(List<Node> hostsToRemove) {
        return findHostRemovalFailure(hostsToRemove, hosts, nodeChildren, availableResources)
                .map(removal -> new HostFailurePath(hostsToRemove, removal));
    }

    private List<Node> getTenants(NodeList allNodes, List<Node> hosts) {
        var parentNames = hosts.stream().map(Node::hostname).collect(Collectors.toSet());
        return allNodes.nodeType(NodeType.tenant).state(relevantNodeStates).stream()
                .filter(t -> parentNames.contains(t.parentHostname().orElse("")))
                .toList();
    }

    private Optional<HostFailurePath> greedyHeuristicFindFailurePath(Map<Node, Integer> heuristic) {
        if (hosts.size() == 0) return Optional.empty();

        List<Node> parentRemovalPriorityList = heuristic.entrySet().stream()
                                                        .sorted(this::hostMitigationOrder)
                                                        .map(Map.Entry::getKey)
                                                        .toList();

        for (int i = 1; i <= parentRemovalPriorityList.size(); i++) {
            List<Node> hostsToRemove = parentRemovalPriorityList.subList(0, i);
            var hostRemovalFailure = findHostRemovalFailure(hostsToRemove);
            if (hostRemovalFailure.isPresent()) return hostRemovalFailure;
        }

        throw new IllegalStateException("No path to failure found. This should be impossible!");
    }

    private int hostMitigationOrder(Map.Entry<Node, Integer> entry1, Map.Entry<Node, Integer> entry2) {
        int result = Integer.compare(entry1.getValue(), entry2.getValue());
        if (result != 0) return result;
        // Mitigate the largest hosts first
        return NodeResourceComparator.defaultOrder().compare(entry2.getKey().resources(), entry1.getKey().resources());
    }

    private Map<String, Node> constructHostnameToNodeMap(List<Node> nodes) {
        return nodes.stream().collect(Collectors.toMap(Node::hostname, n -> n));
    }

    private Map<Node, List<Node>> constructNodeChildrenMap(List<Node> tenants, List<Node> hosts, Map<String, Node> hostnameToNode) {
        Map<Node, List<Node>> nodeChildren = tenants.stream()
                                                    .filter(n -> n.parentHostname().isPresent())
                                                    .filter(n -> hostnameToNode.containsKey(n.parentHostname().get()))
                                                    .collect(Collectors.groupingBy(n -> hostnameToNode.get(n.parentHostname().orElseThrow())));

        for (var host : hosts)
            nodeChildren.putIfAbsent(host, List.of());

        return nodeChildren;
    }

    private Map<Node, AllocationResources> constructAvailableResourcesMap(List<Node> hosts, Map<Node, List<Node>> nodeChildren) {
        Map<Node, AllocationResources> availableResources = new HashMap<>();
        for (var host : hosts) {
            NodeResources hostResources = host.flavor().resources();
            int occupiedIps = 0;
            Set<String> ipPool = host.ipConfig().pool().asSet();
            for (var child : nodeChildren.get(host)) {
                hostResources = hostResources.subtract(child.resources().justNumbers());
                occupiedIps += (int)child.ipConfig().primary().stream().filter(ipPool::contains).count();
            }
            availableResources.put(host, new AllocationResources(hostResources, host.ipConfig().pool().asSet().size() - occupiedIps));
        }

        return availableResources;
    }

    /**
     * Computes a heuristic for each host, with a lower score indicating a higher perceived likelihood that removing
     * the host causes an unrecoverable state
     */
    private Map<Node, Integer> computeMaximalRepeatedRemovals() {
        Map<Node, Integer> timesNodeCanBeRemoved = hosts.stream().collect(Collectors.toMap(Function.identity(),
                                                                                           __ -> Integer.MAX_VALUE));
        for (Node host : hosts) {
            List<Node> children = nodeChildren.get(host);
            if (children.size() == 0) continue;
            Map<Node, AllocationResources> resourceMap = new HashMap<>(availableResources);
            Map<Node, List<Allocation>> containedAllocations = collateAllocations(nodeChildren);

            int timesHostCanBeRemoved = 0;
            Optional<Node> unallocatedNode;
            while (timesHostCanBeRemoved < 100) { // Arbitrary upper bound
                unallocatedNode = tryAllocateNodes(nodeChildren.get(host), hosts, resourceMap, containedAllocations);
                if (unallocatedNode.isEmpty()) {
                    timesHostCanBeRemoved++;
                } else break;
            }
            timesNodeCanBeRemoved.put(host, timesHostCanBeRemoved);
        }

        return timesNodeCanBeRemoved;
    }

    private Map<Node, List<Allocation>> collateAllocations(Map<Node, List<Node>> nodeChildren) {
        return nodeChildren.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                        .map(Node::allocation).flatMap(Optional::stream)
                        .collect(Collectors.toCollection(ArrayList::new))
        ));
    }

    /**
     * Tests whether it's possible to remove the provided hosts.
     * Does not mutate any input variable.
     *
     * @return empty optional if removal is possible, information on what caused the failure otherwise
     */
    private Optional<HostRemovalFailure> findHostRemovalFailure(List<Node> hostsToRemove, List<Node> allHosts,
                                                                Map<Node, List<Node>> nodechildren,
                                                                Map<Node, AllocationResources> availableResources) {
        var containedAllocations = collateAllocations(nodechildren);
        var resourceMap = new HashMap<>(availableResources);
        List<Node> validAllocationTargets = allHosts.stream()
                                                    .filter(h -> !hostsToRemove.contains(h))
                                                    .filter(host -> !host.status().wantToRetire() &&
                                                            !host.status().wantToFail())
                                                    .toList();
        if (validAllocationTargets.size() == 0)
            return Optional.of(HostRemovalFailure.none());

        allocationHistory = new AllocationHistory();
        for (var host : hostsToRemove) {
            Optional<Node> unallocatedNode = tryAllocateNodes(nodechildren.get(host),
                                                              validAllocationTargets,
                                                              resourceMap,
                                                              containedAllocations,
                                                              true);

            if (unallocatedNode.isPresent()) {
                AllocationFailureReasonList failures = collateAllocationFailures(unallocatedNode.get(),
                                                                                 validAllocationTargets,
                                                                                 resourceMap,
                                                                                 containedAllocations);
                return Optional.of(HostRemovalFailure.create(host, unallocatedNode.get(), failures));
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to allocate the listed nodes to a new host, mutating availableResources and containedAllocations,
     * optionally returning the first node to fail, if one does.
     */
    private Optional<Node> tryAllocateNodes(List<Node> nodes,
                                            List<Node> hosts,
                                            Map<Node, AllocationResources> availableResources,
                                            Map<Node, List<Allocation>> containedAllocations) {
        return tryAllocateNodes(nodes, hosts, availableResources, containedAllocations, false);
    }
    private Optional<Node> tryAllocateNodes(List<Node> nodes,
                                            List<Node> hosts,
                                            Map<Node, AllocationResources> availableResources,
                                            Map<Node, List<Allocation>> containedAllocations, boolean withHistory) {
        for (var node : nodes) {
            var newParent = tryAllocateNode(node, hosts, availableResources, containedAllocations);
            if (newParent.isEmpty()) {
                if (withHistory) allocationHistory.addEntry(node, null, 0);
                return Optional.of(node);
            }
            if (withHistory) {
                long eligibleParents =
                    hosts.stream().filter(h ->
                            !violatesParentHostPolicy(node, h, containedAllocations)
                                && availableResources.get(h).satisfies(AllocationResources.from(node.resources()))).count();
                allocationHistory.addEntry(node, newParent.get(), eligibleParents + 1);
            }
        }
        return Optional.empty();
    }

    /** Returns the parent to which the node was allocated, if it was successfully allocated. */
    private Optional<Node> tryAllocateNode(Node node,
                                           List<Node> hosts,
                                           Map<Node, AllocationResources> availableResources,
                                           Map<Node, List<Allocation>> containedAllocations) {
        AllocationResources requiredNodeResources = AllocationResources.from(node);
        for (var host : hosts) {
            var availableHostResources = availableResources.get(host);
            if (violatesParentHostPolicy(node, host, containedAllocations)) {
                continue;
            }
            if (availableHostResources.satisfies(requiredNodeResources)) {
                availableResources.put(host, availableHostResources.subtract(requiredNodeResources));
                if (node.allocation().isPresent()) {
                    containedAllocations.get(host).add(node.allocation().get());
                }
                return Optional.of(host);
            }
        }

        return Optional.empty();
    }

    private static boolean violatesParentHostPolicy(Node node, Node host, Map<Node, List<Allocation>> containedAllocations) {
        if (node.allocation().isEmpty()) return false;
        Allocation nodeAllocation = node.allocation().get();

        if (host.reservedTo()
                .filter(tenantName -> !tenantName.equals(nodeAllocation.owner().tenant()))
                .isPresent()) {
            return true;
        }

        for (var allocation : containedAllocations.get(host)) {
            if (allocation.membership().cluster().satisfies(nodeAllocation.membership().cluster())
                    && allocation.owner().equals(nodeAllocation.owner())) {
                return true;
            }
        }
        return false;
    }

    private AllocationFailureReasonList collateAllocationFailures(Node node, List<Node> hosts,
                                                                  Map<Node, AllocationResources> availableResources,
                                                                  Map<Node, List<Allocation>> containedAllocations) {
        List<AllocationFailureReason> allocationFailureReasons = new ArrayList<>();
        for (var host : hosts) {
            AllocationFailureReason reason = new AllocationFailureReason();
            var availableHostResources = availableResources.get(host);
            reason.violatesParentHostPolicy = violatesParentHostPolicy(node, host, containedAllocations);

            NodeResources l = availableHostResources.nodeResources;
            NodeResources r = node.allocation().map(Allocation::requestedResources).orElse(node.resources());

            if (l.vcpu() < r.vcpu())
                reason.insufficientVcpu = true;
            if (l.memoryGb() < r.memoryGb())
                reason.insufficientMemoryGb = true;
            if (l.diskGb() < r.diskGb())
                reason.insufficientDiskGb = true;
            if (r.diskSpeed() != NodeResources.DiskSpeed.any && r.diskSpeed() != l.diskSpeed())
                reason.incompatibleDiskSpeed = true;
            if (r.storageType() != NodeResources.StorageType.any && r.storageType() != l.storageType())
                reason.incompatibleStorageType = true;
            if (r.architecture() != NodeResources.Architecture.any && r.architecture() != l.architecture())
                reason.incompatibleStorageType = true;
            if (availableHostResources.availableIPs < 1)
                reason.insufficientAvailableIPs = true;

            allocationFailureReasons.add(reason);
        }

        return new AllocationFailureReasonList(allocationFailureReasons);
    }

    /**
     * Contains the list of hosts that, upon being removed, caused an unrecoverable state,
     * as well as the specific host and tenant which caused it.
     */
    public static class HostFailurePath {

        public final List<Node> hostsCausingFailure;
        public final HostRemovalFailure failureReason;

        public HostFailurePath(List<Node> hostsCausingFailure, HostRemovalFailure failureReason) {
            this.hostsCausingFailure = hostsCausingFailure;
            this.failureReason = failureReason;
        }

        @Override
        public String toString() {
            return "failure path: " + failureReason + " upon removing " + hostsCausingFailure;
        }

    }

    /**
     * Data class used for detailing why removing the given tenant from the given host was unsuccessful.
     * A failure might not be caused by failing to allocate a specific tenant, in which case the fields
     * will be empty.
     */
    public static class HostRemovalFailure {

        public final Optional<Node> host;
        public final Optional<Node> tenant;
        public final AllocationFailureReasonList allocationFailures;

        public static HostRemovalFailure none() {
            return new HostRemovalFailure(Optional.empty(),
                                          Optional.empty(),
                                          new AllocationFailureReasonList(List.of()));
        }

        public static HostRemovalFailure create(Node host, Node tenant, AllocationFailureReasonList failureReasons) {
            return new HostRemovalFailure(Optional.of(host),
                                          Optional.of(tenant),
                                          failureReasons);
        }

        private HostRemovalFailure(Optional<Node> host, Optional<Node> tenant, AllocationFailureReasonList allocationFailures) {
            this.host = host;
            this.tenant = tenant;
            this.allocationFailures = allocationFailures;
        }

        @Override
        public String toString() {
            if (host.isEmpty() || tenant.isEmpty()) return "No removal candidates exists";
            return String.format(
                    "Failure to remove host %s" +
                    "\n\tNo new host found for tenant %s:" +
                    "\n\t\tSingular Reasons: %s" +
                    "\n\t\tTotal Reasons:    %s",
                    this.host.get().hostname(),
                    this.tenant.get().hostname(),
                    this.allocationFailures.singularReasonFailures().toString(),
                    this.allocationFailures.toString()
            );
        }
    }

    /** Used to describe the resources required for a tenant, and available to a host. */
    private static class AllocationResources {

        private final NodeResources nodeResources;
        private final int availableIPs;

        public static AllocationResources from(Node node) {
            if (node.allocation().isPresent())
                return from(node.allocation().get().requestedResources());
            else
                return from(node.resources());
        }

        public static AllocationResources from(NodeResources nodeResources) {
            return new AllocationResources(nodeResources, 1);
        }

        public AllocationResources(NodeResources nodeResources, int availableIPs) {
            this.nodeResources = nodeResources;
            this.availableIPs = availableIPs;
        }

        public boolean satisfies(AllocationResources other) {
            if (!this.nodeResources.satisfies(other.nodeResources)) return false;
            return this.availableIPs >= other.availableIPs;
        }

        public AllocationResources subtract(AllocationResources other) {
            return new AllocationResources(this.nodeResources.subtract(other.nodeResources), this.availableIPs - other.availableIPs);
        }

    }

    /**
     * Keeps track of the reason why a host rejected an allocation.
     */
    private static class AllocationFailureReason {

        public boolean insufficientVcpu = false;
        public boolean insufficientMemoryGb = false;
        public boolean insufficientDiskGb = false;
        public boolean incompatibleDiskSpeed = false;
        public boolean incompatibleStorageType = false;
        public boolean incompatibleArchitecture = false;
        public boolean insufficientAvailableIPs = false;
        public boolean violatesParentHostPolicy = false;

        public int numberOfReasons() {
            int n = 0;
            if (insufficientVcpu) n++;
            if (insufficientMemoryGb) n++;
            if (insufficientDiskGb) n++;
            if (incompatibleDiskSpeed) n++;
            if (incompatibleArchitecture) n++;
            if (insufficientAvailableIPs) n++;
            if (violatesParentHostPolicy) n++;
            return n;
        }

        @Override
        public String toString() {
            List<String> reasons = new ArrayList<>();
            if (insufficientVcpu) reasons.add("insufficientVcpu");
            if (insufficientMemoryGb) reasons.add("insufficientMemoryGb");
            if (insufficientDiskGb) reasons.add("insufficientDiskGb");
            if (incompatibleDiskSpeed) reasons.add("incompatibleDiskSpeed");
            if (incompatibleStorageType) reasons.add("incompatibleStorageType");
            if (incompatibleArchitecture) reasons.add("incompatibleArchitecture");
            if (insufficientAvailableIPs) reasons.add("insufficientAvailableIPs");
            if (violatesParentHostPolicy) reasons.add("violatesParentHostPolicy");

            return String.format("[%s]", String.join(", ", reasons));
        }

    }

    /**
     * Provides convenient methods for tallying failures.
     */
    public static class AllocationFailureReasonList {

        private final List<AllocationFailureReason> allocationFailureReasons;

        public AllocationFailureReasonList(List<AllocationFailureReason> allocationFailureReasons) {
            this.allocationFailureReasons = allocationFailureReasons;
        }

        public long insufficientVcpu()         { return allocationFailureReasons.stream().filter(r -> r.insufficientVcpu).count(); }
        public long insufficientMemoryGb()     { return allocationFailureReasons.stream().filter(r -> r.insufficientMemoryGb).count(); }
        public long insufficientDiskGb()       { return allocationFailureReasons.stream().filter(r -> r.insufficientDiskGb).count(); }
        public long incompatibleDiskSpeed()    { return allocationFailureReasons.stream().filter(r -> r.incompatibleDiskSpeed).count(); }
        public long incompatibleStorageType()  { return allocationFailureReasons.stream().filter(r -> r.incompatibleStorageType).count(); }
        public long incompatibleArchitecture() { return allocationFailureReasons.stream().filter(r -> r.incompatibleArchitecture).count(); }
        public long insufficientAvailableIps() { return allocationFailureReasons.stream().filter(r -> r.insufficientAvailableIPs).count(); }
        public long violatesParentHostPolicy() { return allocationFailureReasons.stream().filter(r -> r.violatesParentHostPolicy).count(); }

        public AllocationFailureReasonList singularReasonFailures() {
            return new AllocationFailureReasonList(allocationFailureReasons.stream()
                    .filter(reason -> reason.numberOfReasons() == 1).toList());
        }
        public AllocationFailureReasonList multipleReasonFailures() {
            return new AllocationFailureReasonList(allocationFailureReasons.stream()
                    .filter(reason -> reason.numberOfReasons() > 1).toList());
        }
        public long size() {
            return allocationFailureReasons.size();
        }
        @Override
        public String toString() {
            return String.format("CPU (%3d), Memory (%3d), Disk size (%3d), Disk speed (%3d), Storage type (%3d), Architecture (%3d), IP (%3d), Parent-Host Policy (%3d)",
                    insufficientVcpu(), insufficientMemoryGb(), insufficientDiskGb(), incompatibleDiskSpeed(),
                                 incompatibleStorageType(), incompatibleArchitecture(), insufficientAvailableIps(), violatesParentHostPolicy());
        }

    }

    public static class AllocationHistory {

        public static class Entry {
            public final Node tenant;
            public final Node newParent;
            public final long eligibleParents;

            public Entry(Node tenant, Node newParent, long eligibleParents) {
                this.tenant = tenant;
                this.newParent = newParent;
                this.eligibleParents = eligibleParents;
            }

            @Override
            public String toString() {
                return String.format("%-20s %-65s -> %15s [%3d valid]",
                        tenant.hostname().replaceFirst("\\..+", ""),
                        tenant.resources(),
                        newParent == null ? "x" : newParent.hostname().replaceFirst("\\..+", ""),
                        this.eligibleParents
                );
            }
        }

        public final List<Entry> historyEntries;

        public AllocationHistory() {
            this.historyEntries = new ArrayList<>();
        }

        public void addEntry(Node tenant, Node newParent, long eligibleParents) {
            this.historyEntries.add(new Entry(tenant, newParent, eligibleParents));
        }

        public Set<String> oldParents() {
            Set<String> oldParents = new HashSet<>();
            for (var entry : historyEntries)
                entry.tenant.parentHostname().ifPresent(oldParents::add);
            return oldParents;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder();

            String currentParent = "";
            for (var entry : historyEntries) {
                String parentName = entry.tenant.parentHostname().orElseThrow();
                if (!parentName.equals(currentParent)) {
                    currentParent = parentName;
                    out.append(parentName).append("\n");
                }
                out.append(entry.toString()).append("\n");
            }

            return out.toString();
        }

    }

}
