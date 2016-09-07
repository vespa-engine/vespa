// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.ComparisonChain;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Flavor;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Performs preparation of node activation changes for a single host group in an application.
 *
 * @author bratseth
 */
class GroupPreparer {

    private final NodeRepository nodeRepository;
    private final Clock clock;

    private static final boolean canChangeGroup = true;

    public GroupPreparer(NodeRepository nodeRepository, Clock clock) {
        this.nodeRepository = nodeRepository;
        this.clock = clock;
    }

    /**
     * Ensure sufficient nodes are reserved or active for the given application, group and cluster
     *
     * @param application the application we are allocating to
     * @param cluster the cluster and group we are allocating to
     * @param nodeCount the desired number of nodes to return
     * @param flavor the desired flavor of those nodes
     * @param surplusActiveNodes currently active nodes which are available to be assigned to this group.
     *        This method will remove from this list if it finds it needs additional nodes
     * @param highestIndex the current highest node index among all active nodes in this cluster.
     *        This method will increase this number when it allocates new nodes to the cluster.
     * @return the list of nodes this cluster group will have allocated if activated
     */
     // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
     // but it may not change the set of active nodes, as the active nodes must stay in sync with the
     // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, int nodeCount, Flavor flavor, List<Node> surplusActiveNodes, MutableInteger highestIndex) {
        try (Mutex lock = nodeRepository.lock(application)) {
            NodeList nodeList = new NodeList(application, cluster, nodeCount, flavor, highestIndex);

            // Use active nodes
            nodeList.offer(nodeRepository.getNodes(application, Node.State.active), !canChangeGroup);
            if (nodeList.satisfied()) return nodeList.finalNodes(surplusActiveNodes);

            // Use active nodes from other groups that will otherwise be retired
            List<Node> accepted = nodeList.offer(sortNodeListByCost(surplusActiveNodes), canChangeGroup);
            surplusActiveNodes.removeAll(accepted);
            if (nodeList.satisfied()) return nodeList.finalNodes(surplusActiveNodes);

            // Use previously reserved nodes
            nodeList.offer(nodeRepository.getNodes(application, Node.State.reserved), !canChangeGroup);
            if (nodeList.satisfied()) return nodeList.finalNodes(surplusActiveNodes);

            // Use inactive nodes
            accepted = nodeList.offer(sortNodeListByCost(nodeRepository.getNodes(application, Node.State.inactive)), !canChangeGroup);
            nodeList.update(nodeRepository.reserve(accepted));
            if (nodeList.satisfied()) return nodeList.finalNodes(surplusActiveNodes);

            // Use new, ready nodes. Lock ready pool to ensure that nodes are not grabbed by others.
            try (Mutex readyLock = nodeRepository.lockUnallocated()) {
                List<Node> readyNodes = nodeRepository.getNodes(Node.Type.tenant, Node.State.ready);
                accepted = nodeList.offer(stripeOverHosts(sortNodeListByCost(readyNodes)), !canChangeGroup);
                nodeList.update(nodeRepository.reserve(accepted));
            }
            if (nodeList.satisfied()) return nodeList.finalNodes(surplusActiveNodes);

            if (nodeList.whatAboutUsingRetiredNodes()) {
                throw new OutOfCapacityException("Could not satisfy request for " + nodeCount +
                                                 " nodes of " + flavor + " for " + cluster +
                                                 " because we want to retire existing nodes.");
            }
            if (nodeList.whatAboutUsingVMs()) {
                throw new OutOfCapacityException("Could not satisfy request for " + nodeCount +
                                                 " nodes of " + flavor + " for " + cluster +
                                                 " because too many have same parentHost.");
            }
            throw new OutOfCapacityException("Could not satisfy request for " + nodeCount +
                                             " nodes of " + flavor + " for " + cluster + ".");
        }
    }

    /** Sort nodes according to their cost, and if the cost is equal, sort by hostname (to get stable tests) */
    private List<Node> sortNodeListByCost(List<Node> nodeList) {
        Collections.sort(nodeList, (n1, n2) -> ComparisonChain.start()
                .compare(n1.flavor().cost(), n2.flavor().cost())
                .compare(n1.hostname(), n2.hostname())
                .result()
        );
        return nodeList;
    }

    /** Return the input nodes in an order striped over their parent hosts */
    static List<Node> stripeOverHosts(List<Node> input) {
        List<Node> output = new ArrayList<>(input.size());

        // first deal with nodes having a parent host
        long nodesHavingParent = input.stream()
            .filter(n -> n.parentHostname().isPresent())
            .collect(Collectors.counting());
        if (nodesHavingParent > 0) {
            // Make a map where each parent host maps to its list of child nodes
            Map<String, List<Node>> byParentHosts = input.stream()
                .filter(n -> n.parentHostname().isPresent())
                .collect(Collectors.groupingBy(n -> n.parentHostname().get()));

            // sort keys, those parent hosts with the most (remaining) ready nodes first
            List<String> sortedParentHosts = byParentHosts
                .keySet().stream()
                .sorted((k1, k2) -> byParentHosts.get(k2).size() - byParentHosts.get(k1).size())
                .collect(Collectors.toList());
            while (nodesHavingParent > 0) {
                // take one node from each parent host, round-robin.
                for (String k : sortedParentHosts) {
                    List<Node> leftFromHost = byParentHosts.get(k);
                    if (! leftFromHost.isEmpty()) {
                        output.add(leftFromHost.remove(0));
                        --nodesHavingParent;
                    }
                }
            }
        }

        // now add non-VMs (nodes without a parent):
        input.stream()
            .filter(n -> ! n.parentHostname().isPresent())
            .forEach(n -> output.add(n));
        return output;
    }

    /** Used to manage a list of nodes during the node reservation process */
    private class NodeList {

        /** The application this list is for */
        private final ApplicationId application;

        /** The cluster this list is for */
        private final ClusterSpec cluster;

        /** The requested capacity of the list */
        private final int requestedNodes;

        /** The requested node flavor */
        private final Flavor requestedFlavor;

        /** The nodes this has accepted so far */
        private final Set<Node> nodes = new LinkedHashSet<>();

        /** The number of nodes in the accepted nodes which are of the requested flavor */
        private int acceptedOfRequestedFlavor = 0;

        /** The number of nodes rejected because of clashing parentHostname */
        private int rejectedWithClashingParentHost = 0;

        /** The number of nodes that just now was changed to retired */
        private int wasRetiredJustNow = 0;

        /** The node indexes to verify uniqueness of each members index */
        private Set<Integer> indexes = new HashSet<>();

        /** The next membership index to assign to a new node */
        private MutableInteger highestIndex;

        public NodeList(ApplicationId application, ClusterSpec cluster, int requestedNodes, Flavor requestedFlavor, MutableInteger highestIndex) {
            this.application = application;
            this.cluster = cluster;
            this.requestedNodes = requestedNodes;
            this.requestedFlavor = requestedFlavor;
            this.highestIndex = highestIndex;
        }

        /**
         * Offer some nodes to this. The nodes may have an allocation to a different application or cluster,
         * an allocation to this cluster, or no current allocation (in which case one is assigned).
         * <p>
         * Note that if unallocated nodes are offered before allocated nodes, this will unnecessarily
         * reject allocated nodes due to index duplicates.
         *
         * @param offeredNodes the nodes which are potentially on offer. These may belong to a different application etc.
         * @param canChangeGroup whether it is ok to change the group the offered node is to belong to if necessary
         * @return the subset of offeredNodes which was accepted, with the correct allocation assigned
         */
        public List<Node> offer(List<Node> offeredNodes, boolean canChangeGroup) {
            List<Node> accepted = new ArrayList<>();
            for (Node offered : offeredNodes) {
                boolean wantToRetireNode = false;
                if (offered.allocation().isPresent()) {
                    ClusterMembership membership = offered.allocation().get().membership();
                    if ( ! offered.allocation().get().owner().equals(application)) continue; // wrong application
                    if ( ! membership.cluster().equalsIgnoringGroup(cluster)) continue; // wrong cluster id/type
                    if ( (! canChangeGroup || satisfied()) && ! membership.cluster().group().equals(cluster.group())) continue; // wrong group and we can't or have no reason to change it
                    if ( offered.allocation().get().removable()) continue; // don't accept; causes removal
                    if ( indexes.contains(membership.index())) continue; // duplicate index (just to be sure)

                    // conditions on which we want to retire nodes that were allocated previously
                    if ( offeredNodeHasParentHostnameAlreadyAccepted(this.nodes, offered)) wantToRetireNode = true;
                    if ( !hasCompatibleFlavor(offered)) wantToRetireNode = true;

                    if ( ( !satisfied() && hasCompatibleFlavor(offered)) || acceptToRetire(offered) )
                        accepted.add(acceptNode(offered, wantToRetireNode));
                }
                else if (! satisfied() && hasCompatibleFlavor(offered)) {
                    if ( offeredNodeHasParentHostnameAlreadyAccepted(this.nodes, offered)) {
                        ++rejectedWithClashingParentHost;
                        continue;
                    }
                    Node alloc = offered.allocate(application, ClusterMembership.from(cluster, highestIndex.add(1)), clock.instant());
                    accepted.add(acceptNode(alloc, wantToRetireNode));
                }
            }

            return accepted;
        }

        private boolean offeredNodeHasParentHostnameAlreadyAccepted(Collection<Node> accepted, Node offered) {
            for (Node acceptedNode : accepted) {
                if (acceptedNode.parentHostname().isPresent() && offered.parentHostname().isPresent() &&
                    acceptedNode.parentHostname().get().equals(offered.parentHostname().get())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns whether this node should be accepted into the cluster even if it is not currently desired
         * (already enough nodes, or wrong flavor).
         * Such nodes will be marked retired during finalization of the list of accepted nodes.
         * The conditions for this are
         * <ul>
         * <li>This is a content node. These must always be retired before being removed to allow the cluster to
         * migrate away data.
         * <li>This is a container node and it is not desired due to having the wrong flavor. In this case this
         * will (normally) obtain for all the current nodes in the cluster and so retiring before removing must
         * be used to avoid removing all the current nodes at once, before the newly allocated replacements are
         * initialized. (In the other case, where a container node is not desired because we have enough nodes we
         * do want to remove it immediately to get immediate feedback on how the size reduction works out.)
         * </ul>
         */
        private boolean acceptToRetire(Node node) {
            if (node.state() != Node.State.active) return false;
            if (! node.allocation().get().membership().cluster().group().equals(cluster.group())) return false;

            return (cluster.type() == ClusterSpec.Type.content) ||
                   (cluster.type() == ClusterSpec.Type.container && ! hasCompatibleFlavor(node));
        }

        private boolean hasCompatibleFlavor(Node node) {
            return node.flavor().satisfies(requestedFlavor);
        }

        /** Updates the state of some existing nodes in this list by replacing them by id with the given instances. */
        public void update(List<Node> updatedNodes) {
            nodes.removeAll(updatedNodes);
            nodes.addAll(updatedNodes);
        }

        private Node acceptNode(Node node, boolean wantToRetire) {
            if (! wantToRetire) {
                if ( ! node.state().equals(Node.State.active)) {
                    // reactivated node - make sure its not retired
                    node = node.unretire();
                }
                acceptedOfRequestedFlavor++;
            } else {
                ++wasRetiredJustNow;
                // retire nodes which are of an unwanted flavor
                // or have an overlapping parent host
                node = node.retireByApplication(clock.instant());
            }
            if ( ! node.allocation().get().membership().cluster().equals(cluster)) {
                // group may be different
                node = setCluster(cluster, node);
            }
            indexes.add(node.allocation().get().membership().index());
            highestIndex.set(Math.max(highestIndex.get(), node.allocation().get().membership().index()));
            nodes.add(node);
            return node;
        }

        private Node setCluster(ClusterSpec cluster, Node node) {
            ClusterMembership membership = node.allocation().get().membership().changeCluster(cluster);
            return node.setAllocation(node.allocation().get().changeMembership(membership));
        }

        /** Returns true if we have accepted at least the requested number of nodes of the requested flavor */
        public boolean satisfied() {
            return acceptedOfRequestedFlavor >= requestedNodes;
        }

        public boolean whatAboutUsingRetiredNodes() {
            return acceptedOfRequestedFlavor + wasRetiredJustNow >= requestedNodes;
        }

        public boolean whatAboutUsingVMs() {
            return acceptedOfRequestedFlavor + rejectedWithClashingParentHost >= requestedNodes;
        }

        /**
         * Make the number of <i>non-retired</i> nodes in the list equal to the requested number
         * of nodes, and retire the rest of the list. Only retire currently active nodes.
         * Prefer to retire nodes of the wrong flavor.
         * Make as few changes to the retired set as possible.
         *
         * @param surplusNodes this will add nodes not any longer needed by this group to this list
         * @return the final list of nodes
         */
        public List<Node> finalNodes(List<Node> surplusNodes) {
            long currentRetired = nodes.stream().filter(node -> node.allocation().get().membership().retired()).count();
            long surplus = nodes.size() - requestedNodes - currentRetired;

            List<Node> changedNodes = new ArrayList<>();
            if (surplus > 0) { // retire until surplus is 0, prefer to retire higher indexes to minimize redistribution
                for (Node node : byDecreasingIndex(nodes)) {
                    if ( ! node.allocation().get().membership().retired() && node.state().equals(Node.State.active)) {
                        changedNodes.add(node.retireByApplication(clock.instant()));
                        surplusNodes.add(node); // offer this node to other groups
                        if (--surplus == 0) break;
                    }
                }
            }
            else if (surplus < 0) { // unretire until surplus is 0
                for (Node node : byIncreasingIndex(nodes)) {
                    if ( node.allocation().get().membership().retired() && hasCompatibleFlavor(node)) {
                        changedNodes.add(node.unretire());
                        if (++surplus == 0) break;
                    }
                }
            }
            update(changedNodes);
            return new ArrayList<>(nodes);
        }

        private List<Node> byDecreasingIndex(Set<Node> nodes) {
            return nodes.stream().sorted(nodeIndexComparator().reversed()).collect(Collectors.toList());
        }

        private List<Node> byIncreasingIndex(Set<Node> nodes) {
            return nodes.stream().sorted(nodeIndexComparator()).collect(Collectors.toList());
        }
        
        private Comparator<Node> nodeIndexComparator() {
            return Comparator.comparing((Node n) -> n.allocation().get().membership().index());
        }

    }

}
