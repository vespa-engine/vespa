package com.yahoo.vespa.hosted.provision.maintenance;

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

public class NodeAlerter extends Maintainer {

    private final Metric metric;
    private final NodeRepository nodeRepository;
    private static final Logger log = Logger.getLogger(NodeAlerter.class.getName());

    NodeAlerter(NodeRepository nodeRepository,
                Metric metric,
                Duration interval) {
        super(nodeRepository, interval);
        this.nodeRepository = nodeRepository;
        this.metric = metric;
    }

    @Override
    protected void maintain() {
        if (metric != null) {
            int worstCaseHostLoss = worstCaseHostLossLeadingToFailure().hostsCausingFailure.size();
            metric.set("hostedVespa.spareHostCapacity", worstCaseHostLoss - 1, null);
            metric.set("hostedVespa.overcommittedNodes", countOvercommittedNodes(), null);
        }
    }

    class HostFailurePath {
        List<Node> hostsCausingFailure;
        HostRemovalFailure failureReason;
    }

    HostFailurePath worstCaseHostLossLeadingToFailure() {
        List<Node> tenants = nodeRepository.getNodes(NodeType.tenant);
        List<Node> hosts = nodeRepository.getNodes(NodeType.host);
        Map<String, Node> nodeMap = constructHostnameToNodeMap(hosts);
        Map<Node, List<Node>> nodeChildren = constructNodeChildrenMap(tenants, hosts, nodeMap);
        Map<Node, AllocationResources> availableResources = constructAvailableResourcesMap(hosts, nodeChildren);

        Map<Node, Integer> timesNodeCanBeRemoved = computeMaximalRepeatedRemovals(hosts, nodeChildren, availableResources);
        return greedyHeuristicFindFailurePath(timesNodeCanBeRemoved, hosts, nodeChildren, availableResources);
    }

    HostFailurePath greedyHeuristicFindFailurePath(Map<Node, Integer> heuristic, List<Node> hosts,
                                                   Map<Node, List<Node>> nodeChildren,
                                                   Map<Node, AllocationResources> availableResources) {
        if (hosts.size() == 0) return null;
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
                return failurePath;
            }
        }

        throw new IllegalStateException("No path to failure found. This should be impossible!");
    }

    int countOvercommittedNodes() {
        List<Node> tenants = nodeRepository.getNodes(NodeType.tenant);
        List<Node> hosts = nodeRepository.getNodes(NodeType.host);
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

    Map<String, Node> constructHostnameToNodeMap(List<Node> nodes) {
        return nodes.stream().collect(Collectors.toMap(Node::hostname, n -> n));
    }

    Map<Node, List<Node>> constructNodeChildrenMap(List<Node> tenants, List<Node> hosts, Map<String, Node> hostnameToNode) {
        Map<Node, List<Node>> nodeChildren = tenants.stream()
                .filter(n -> n.parentHostname().isPresent())
                .collect(Collectors.groupingBy(
                        n -> hostnameToNode.get(n.parentHostname().orElseThrow())));
        if (nodeChildren.get(null) != null) {
            List<Node> hasNullParents = nodeChildren.get(null);
            throw new IllegalStateException("Hostname(s) not present in hostname map : " +
                    hasNullParents.stream().map(n -> n.parentHostname().orElseThrow()).collect(Collectors.joining(", ")));
        }
        for (var host : hosts) nodeChildren.putIfAbsent(host, List.of());

        return nodeChildren;
    }

    Map<Node, AllocationResources> constructAvailableResourcesMap(List<Node> hosts, Map<Node, List<Node>> nodeChildren) {
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
    Map<Node, Integer> computeMaximalRepeatedRemovals(List<Node> hosts, Map<Node, List<Node>> nodeChildren,
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

    List<Node> findOvercommittedNodes(Map<Node, AllocationResources> availableResources) {
        List<Node> overcommittedNodes = new ArrayList<>();
        for (var entry : availableResources.entrySet()) {
            var resources = entry.getValue().nodeResources;
            if (resources.vcpu() < 0 || resources.memoryGb() < 0 || resources.diskGb() < 0) {
                overcommittedNodes.add(entry.getKey());
            }
        }
        return overcommittedNodes;
    }

    Map<Node, List<Allocation>> collateAllocations(Map<Node, List<Node>> nodeChildren) {
        return nodeChildren.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                        .map(Node::allocation).flatMap(Optional::stream)
                        .collect(Collectors.toList())
        ));
    }

    class HostRemovalFailure {
        Optional<Node> host;
        Optional<Node> tenant;
        AllocationFailureReasonList failureReasons;
        public HostRemovalFailure() {
            this.host = Optional.empty();
            this.tenant = Optional.empty();
            this.failureReasons = new AllocationFailureReasonList(List.of());
        }
        public HostRemovalFailure(Node host, Node tenant, AllocationFailureReasonList failureReasons) {
            this.host = Optional.of(host);
            this.tenant = Optional.of(tenant);
            this.failureReasons = failureReasons;
        }
    }

    /**
     * Tests whether it's possible to remove the provided hosts.
     * Does not mutate any input variable.
     * @return Empty optional if removal is possible, information on what caused the failure otherwise
     */
    Optional<HostRemovalFailure> findHostRemovalFailure(List<Node> hostsToRemove, List<Node> allHosts,
                                                        Map<Node, List<Node>> nodechildren,
                                                        Map<Node, AllocationResources> availableResources) {
        var containedAllocations = collateAllocations(nodechildren);
        var resourceMap = new HashMap<>(availableResources);
        List<Node> validAllocationTargets = allHosts.stream()
                .filter(h -> !hostsToRemove.contains(h))
                .collect(Collectors.toList());
        if (validAllocationTargets.size() == 0) {
            return Optional.of(new HostRemovalFailure());
        }

        for (var host : hostsToRemove) {
            Optional<Node> unallocatedNode = tryAllocateNodes(nodechildren.get(host),
                    validAllocationTargets, resourceMap, containedAllocations);

            if (unallocatedNode.isPresent()) {
                return Optional.of(new HostRemovalFailure(host, unallocatedNode.get(),
                        collateAllocationFailures(unallocatedNode.get(), validAllocationTargets, resourceMap, containedAllocations)));
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to allocate the listed nodes to a new host, mutating availableResources and containedAllocations,
     * optionally returning the first node to fail, if one does.
     * */
    Optional<Node> tryAllocateNodes(List<Node> nodes, List<Node> hosts,
                                    Map<Node, AllocationResources> availableResources,
                                    Map<Node, List<Allocation>> containedAllocations) {
        for (var node : nodes) {
            if (!tryAllocateNode(node, hosts, availableResources, containedAllocations)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    boolean tryAllocateNode(Node node, List<Node> hosts,
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

    boolean violatesParentHostPolicy(Node node, Node host, Map<Node, List<Allocation>> containedAllocations) {
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

    class AllocationFailureReasonList {
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

    AllocationFailureReasonList collateAllocationFailures(Node node, List<Node> hosts,
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
