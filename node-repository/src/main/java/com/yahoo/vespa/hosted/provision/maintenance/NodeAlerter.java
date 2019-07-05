package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.util.*;
import java.util.function.Function;

/**
 * Performs analysis on the node repository to produce metrics that can be used for alerts if the repository is in an
 * undesirable state.
 * These metrics include:
 * Spare host capacity, or how many hosts the repository can stand to lose without ending up in a situation where it's
 * unable to find a new home for orphaned tenants.
 * Overcommitted hosts, which tracks if there are any hosts whose capacity is less than the sum of its children's.
 *
 * @author mgimle
 */
public class NodeAlerter extends Maintainer {

    private final Metric metric;
    private final NodeRepository nodeRepository;
    private static final Logger log = Logger.getLogger(NodeAlerter.class.getName());

    NodeAlerter(NodeRepository nodeRepository,
                Metric metric,
                Duration interval) {
        super(nodeRepository, interval);
        this.nodeRepository = nodeRepository;
        this.metric = Objects.requireNonNull(metric);
    }

    @Override
    protected void maintain() {
        Optional<HostFailurePath> failurePath = worstCaseHostLossLeadingToFailure();
        if (failurePath.isPresent()) {
            int worstCaseHostLoss = failurePath.get().hostsCausingFailure.size();
            metric.set("hostedVespa.spareHostCapacity", worstCaseHostLoss - 1, null);
            metric.set("hostedVespa.overcommittedHosts", countOvercommittedHosts(), null);
        }
    }

    /**
     * Contains the list of hosts that, upon being removed, caused an unrecoverable state,
     * as well as the specific host and tenant which caused it.
     */
    public static class HostFailurePath {
        List<Node> hostsCausingFailure;
        HostRemovalFailure failureReason;
    }

    public Optional<HostFailurePath> worstCaseHostLossLeadingToFailure() {
        List<Node> hosts = filterHosts(nodeRepository.getNodes(NodeType.host));
        List<Node> tenants = filterTenants(nodeRepository.getNodes(NodeType.tenant), hosts);
        Map<String, Node> nodeMap = constructHostnameToNodeMap(hosts);
        Map<Node, List<Node>> nodeChildren = constructNodeChildrenMap(tenants, hosts, nodeMap);
        Map<Node, AllocationResources> availableResources = constructAvailableResourcesMap(hosts, nodeChildren);

        Map<Node, Integer> timesNodeCanBeRemoved = computeMaximalRepeatedRemovals(hosts, nodeChildren, availableResources);
        return greedyHeuristicFindFailurePath(timesNodeCanBeRemoved, hosts, nodeChildren, availableResources);
    }

    private List<Node> filterTenants(List<Node> tenants, List<Node> hosts) {
        var parentNames = hosts.stream().map(Node::hostname).collect(Collectors.toSet());
        return tenants.stream()
                .filter((t -> t.flavor().getType() != Flavor.Type.BARE_METAL
                                && t.state() != Node.State.failed
                                && t.state() != Node.State.parked))
                .filter(t -> parentNames.contains(t.parentHostname().orElse("")))
                .collect(Collectors.toList());
    }

    private List<Node> filterHosts(List<Node> hosts) {
        return hosts.stream()
                .filter(h -> h.state() != Node.State.failed && h.state() != Node.State.parked)
                .collect(Collectors.toList());
    }

    private Optional<HostFailurePath> greedyHeuristicFindFailurePath(Map<Node, Integer> heuristic, List<Node> hosts,
                                                   Map<Node, List<Node>> nodeChildren,
                                                   Map<Node, AllocationResources> availableResources) {
        if (hosts.size() == 0) return Optional.empty();
        List<Node> parentRemovalPriorityList = heuristic.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        for (int i = 1; i <= parentRemovalPriorityList.size(); i++) {
            List<Node> hostsToRemove = parentRemovalPriorityList.subList(0, i);
            var hostRemovalFailure = findHostRemovalFailure(hostsToRemove, hosts, nodeChildren, availableResources);
            if (hostRemovalFailure.isPresent()) {
                HostFailurePath failurePath = new HostFailurePath();
                failurePath.hostsCausingFailure = hostsToRemove;
                failurePath.failureReason = hostRemovalFailure.get();
                return Optional.of(failurePath);
            }
        }

        throw new IllegalStateException("No path to failure found. This should be impossible!");
    }

    int countOvercommittedHosts() {
        List<Node> hosts = filterHosts(nodeRepository.getNodes(NodeType.host));
        List<Node> tenants = filterTenants(nodeRepository.getNodes(NodeType.tenant), hosts);
        var nodeMap = constructHostnameToNodeMap(hosts);
        var nodeChildren = constructNodeChildrenMap(tenants, hosts, nodeMap);
        var availableResources = constructAvailableResourcesMap(hosts, nodeChildren);

        List<Node> overcommittedNodes = findOvercommittedNodes(availableResources);
        if (overcommittedNodes.size() != 0) {
            log.log(LogLevel.WARNING, String.format("%d nodes are overcommitted! [ %s ]", overcommittedNodes.size(),
                    overcommittedNodes.stream().map(Node::hostname).collect(Collectors.joining(", "))));
        }
        return overcommittedNodes.size();
    }

    private Map<String, Node> constructHostnameToNodeMap(List<Node> nodes) {
        return nodes.stream().collect(Collectors.toMap(Node::hostname, n -> n));
    }

    private Map<Node, List<Node>> constructNodeChildrenMap(List<Node> tenants, List<Node> hosts, Map<String, Node> hostnameToNode) {
        Map<Optional<Node>, List<Node>> possibleNodeChildren = tenants.stream()
                .filter(n -> n.parentHostname().isPresent())
                .collect(Collectors.groupingBy(
                        n -> Optional.ofNullable(hostnameToNode.get(n.parentHostname().orElseThrow()))));
        possibleNodeChildren.remove(Optional.<Node>empty());
        Map<Node, List<Node>> nodeChildren = possibleNodeChildren.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().orElseThrow(), Map.Entry::getValue));

        for (var host : hosts) nodeChildren.putIfAbsent(host, List.of());

        return nodeChildren;
    }

    private Map<Node, AllocationResources> constructAvailableResourcesMap(List<Node> hosts, Map<Node, List<Node>> nodeChildren) {
        Map<Node, AllocationResources> availableResources = new HashMap<>();
        for (var host : hosts) {
            NodeResources hostResources = host.flavor().resources();
            int occupiedIps = 0;
            Set<String> ipPool = host.ipAddressPool().asSet();
            for (var child : nodeChildren.get(host)) {
                hostResources = hostResources.subtract(child.flavor().resources());
                occupiedIps += child.ipAddresses().stream().filter(ipPool::contains).count();
            }
            availableResources.put(host, new AllocationResources(hostResources, host.ipAddressPool().asSet().size() - occupiedIps));
        }

        return availableResources;
    }

    /**
     * Computes a heuristic for each host, with a lower score indicating a higher perceived likelihood that removing
     * the host causes an unrecoverable state
     */
    private Map<Node, Integer> computeMaximalRepeatedRemovals(List<Node> hosts, Map<Node, List<Node>> nodeChildren,
                                                      Map<Node, AllocationResources> availableResources) {
        Map<Node, Integer> timesNodeCanBeRemoved = hosts.stream().collect(Collectors.toMap(
                Function.identity(),
                _x -> Integer.MAX_VALUE
        ));
        for (Node host : hosts) {
            Map<Node, AllocationResources> resourceMap = new HashMap<>(availableResources);
            Map<Node, List<Allocation>> containedAllocations = collateAllocations(nodeChildren);

            int timesHostCanBeRemoved = 0;
            Optional<Node> unallocatedTenant;
            while (timesHostCanBeRemoved < 1000) { // Arbritrary upper bound
                unallocatedTenant = tryAllocateNodes(nodeChildren.get(host), hosts, resourceMap, containedAllocations);
                if (unallocatedTenant.isEmpty()) {
                    timesHostCanBeRemoved++;
                } else break;
            }
            timesNodeCanBeRemoved.put(host, timesHostCanBeRemoved);
        }

        return timesNodeCanBeRemoved;
    }

    private List<Node> findOvercommittedNodes(Map<Node, AllocationResources> availableResources) {
        List<Node> overcommittedNodes = new ArrayList<>();
        for (var entry : availableResources.entrySet()) {
            var resources = entry.getValue().nodeResources;
            if (resources.vcpu() < 0 || resources.memoryGb() < 0 || resources.diskGb() < 0) {
                overcommittedNodes.add(entry.getKey());
            }
        }
        return overcommittedNodes;
    }

    private Map<Node, List<Allocation>> collateAllocations(Map<Node, List<Node>> nodeChildren) {
        return nodeChildren.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                        .map(Node::allocation).flatMap(Optional::stream)
                        .collect(Collectors.toList())
        ));
    }

    /**
     * Data class used for detailing why removing the given tenant from the given host was unsuccessful.
     * A failure might not be caused by failing to allocate a specific tenant, in which case the fields
     * will be empty.
     */
    public static class HostRemovalFailure {
        Optional<Node> host;
        Optional<Node> tenant;
        AllocationFailureReasonList failureReasons;
        public static HostRemovalFailure none() {
            return new HostRemovalFailure(
                    Optional.empty(),
                    Optional.empty(),
                    new AllocationFailureReasonList(List.of()));
        }
        public static HostRemovalFailure create(Node host, Node tenant, AllocationFailureReasonList failureReasons) {
            return new HostRemovalFailure(
                    Optional.of(host),
                    Optional.of(tenant),
                    failureReasons);
        }
        private HostRemovalFailure(Optional<Node> host, Optional<Node> tenant, AllocationFailureReasonList failureReasons) {
            this.host = host;
            this.tenant = tenant;
            this.failureReasons = failureReasons;
        }
    }

    /**
     * Tests whether it's possible to remove the provided hosts.
     * Does not mutate any input variable.
     * @return Empty optional if removal is possible, information on what caused the failure otherwise
     */
    private Optional<HostRemovalFailure> findHostRemovalFailure(List<Node> hostsToRemove, List<Node> allHosts,
                                                        Map<Node, List<Node>> nodechildren,
                                                        Map<Node, AllocationResources> availableResources) {
        var containedAllocations = collateAllocations(nodechildren);
        var resourceMap = new HashMap<>(availableResources);
        List<Node> validAllocationTargets = allHosts.stream()
                .filter(h -> !hostsToRemove.contains(h))
                .collect(Collectors.toList());
        if (validAllocationTargets.size() == 0) {
            return Optional.of(HostRemovalFailure.none());
        }

        for (var host : hostsToRemove) {
            Optional<Node> unallocatedNode = tryAllocateNodes(nodechildren.get(host),
                    validAllocationTargets, resourceMap, containedAllocations);

            if (unallocatedNode.isPresent()) {
                return Optional.of(HostRemovalFailure.create(host, unallocatedNode.get(),
                        collateAllocationFailures(unallocatedNode.get(), validAllocationTargets,
                                resourceMap, containedAllocations)));
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to allocate the listed nodes to a new host, mutating availableResources and containedAllocations,
     * optionally returning the first node to fail, if one does.
     * */
    private Optional<Node> tryAllocateNodes(List<Node> nodes, List<Node> hosts,
                                    Map<Node, AllocationResources> availableResources,
                                    Map<Node, List<Allocation>> containedAllocations) {
        for (var node : nodes) {
            if (!tryAllocateNode(node, hosts, availableResources, containedAllocations)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private boolean tryAllocateNode(Node node, List<Node> hosts,
                            Map<Node, AllocationResources> availableResources,
                            Map<Node, List<Allocation>> containedAllocations) {
        AllocationResources requiredNodeResources = AllocationResources.from(node.flavor().resources());
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
                return true;
            }
        }

        return false;
    }

    /**
     * Used to describe the resources required for a tenant, and available to a host.
     */
    private static class AllocationResources {
        NodeResources nodeResources;
        int availableIPs;

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

    private boolean violatesParentHostPolicy(Node node, Node host, Map<Node, List<Allocation>> containedAllocations) {
        if (node.allocation().isEmpty()) return false;
        Allocation nodeAllocation = node.allocation().get();
        for (var allocation : containedAllocations.get(host)) {
            if (allocation.membership().cluster().equalsIgnoringGroupAndVespaVersion(nodeAllocation.membership().cluster())
                    && allocation.owner().equals(nodeAllocation.owner())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps track of the reason why a host rejected an allocation.
     */
    private class AllocationFailureReason {
        Node host;
        public AllocationFailureReason (Node host) {
            this.host = host;
        }
        public boolean insufficientVcpu = false;
        public boolean insufficientMemoryGb = false;
        public boolean insufficientDiskGb = false;
        public boolean incompatibleDiskSpeed = false;
        public boolean insufficientAvailableIPs = false;
        public boolean violatesParentHostPolicy = false;

        public int numberOfReasons() {
            int n = 0;
            if (insufficientVcpu) n++;
            if (insufficientMemoryGb) n++;
            if (insufficientDiskGb) n++;
            if (incompatibleDiskSpeed) n++;
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
            if (insufficientAvailableIPs) reasons.add("insufficientAvailableIPs");
            if (violatesParentHostPolicy) reasons.add("violatesParentHostPolicy");

            return String.format("[%s]", String.join(", ", reasons));
        }
    }

    /**
     * Provides convenient methods for tallying failures.
     */
    public static class AllocationFailureReasonList {
        private List<AllocationFailureReason> allocationFailureReasons;
        public AllocationFailureReasonList(List<AllocationFailureReason> allocationFailureReasons) {
            this.allocationFailureReasons = allocationFailureReasons;
        }

        long insufficientVcpu()         { return allocationFailureReasons.stream().filter(r -> r.insufficientVcpu).count(); }
        long insufficientMemoryGb()     { return allocationFailureReasons.stream().filter(r -> r.insufficientMemoryGb).count(); }
        long insufficientDiskGb()       { return allocationFailureReasons.stream().filter(r -> r.insufficientDiskGb).count(); }
        long incompatibleDiskSpeed()    { return allocationFailureReasons.stream().filter(r -> r.incompatibleDiskSpeed).count(); }
        long insufficientAvailableIps() { return allocationFailureReasons.stream().filter(r -> r.insufficientAvailableIPs).count(); }
        long violatesParentHostPolicy() { return allocationFailureReasons.stream().filter(r -> r.violatesParentHostPolicy).count(); }

        public AllocationFailureReasonList singularReasonFailures() {
            return new AllocationFailureReasonList(allocationFailureReasons.stream()
                .filter(reason -> reason.numberOfReasons() == 1).collect(Collectors.toList()));
        }
        public AllocationFailureReasonList multipleReasonFailures() {
            return new AllocationFailureReasonList(allocationFailureReasons.stream()
                    .filter(reason -> reason.numberOfReasons() > 1).collect(Collectors.toList()));
        }
        public long size() {
            return allocationFailureReasons.size();
        }
        @Override
        public String toString() {
            return String.format("CPU (%3d), Memory (%3d), Disk size (%3d), Disk speed (%3d), IP (%3d), Parent-Host Policy (%3d)",
                insufficientVcpu(), insufficientMemoryGb(), insufficientDiskGb(),
                incompatibleDiskSpeed(), insufficientAvailableIps(), violatesParentHostPolicy());
        }
    }

    private AllocationFailureReasonList collateAllocationFailures(Node node, List<Node> hosts,
                                                          Map<Node, AllocationResources> availableResources,
                                                          Map<Node, List<Allocation>> containedAllocations) {
        List<AllocationFailureReason> allocationFailureReasons = new ArrayList<>();
        for (var host : hosts) {
            AllocationFailureReason reason = new AllocationFailureReason(host);
            var availableHostResources = availableResources.get(host);
            reason.violatesParentHostPolicy = violatesParentHostPolicy(node, host, containedAllocations);

            NodeResources l = availableHostResources.nodeResources;
            NodeResources r = node.flavor().resources();
            if (l.vcpu()      < r.vcpu())                   { reason.insufficientVcpu = true;         }
            if (l.memoryGb()  < r.memoryGb())               { reason.insufficientMemoryGb = true;     }
            if (l.diskGb()    < r.diskGb())                 { reason.insufficientDiskGb = true;       }
            if (r.diskSpeed() != NodeResources.DiskSpeed.any && r.diskSpeed() != l.diskSpeed())
                                                            { reason.incompatibleDiskSpeed = true;    }
            if (availableHostResources.availableIPs < 1)    { reason.insufficientAvailableIPs = true; }

            allocationFailureReasons.add(reason);
        }

        return new AllocationFailureReasonList(allocationFailureReasons);
    }
}
