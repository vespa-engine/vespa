// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.MaintenanceDeployment.Move;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostCapacity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A maintainer which attempts to ensure there is spare capacity available in chunks which can fit
 * all node resource configuration in use, such that the system is able to quickly replace a failed node
 * if necessary.
 *
 * This also emits the following metrics:
 * - Overcommitted hosts: Hosts whose capacity is less than the sum of its children's
 * - Spare host capacity, or how many hosts the repository can stand to lose without ending up in a situation where it's
 *   unable to find a new home for orphaned tenants.
 *
 * @author mgimle
 * @author bratseth
 */
public class SpareCapacityMaintainer extends NodeRepositoryMaintainer {

    private static final int maxMoves = 5;

    private final Deployer deployer;
    private final Metric metric;

    public SpareCapacityMaintainer(Deployer deployer,
                                   NodeRepository nodeRepository,
                                   Metric metric,
                                   Duration interval) {
        super(nodeRepository, interval);
        this.deployer = deployer;
        this.metric = metric;
    }

    @Override
    protected void maintain() {
        if ( ! nodeRepository().zone().getCloud().allowHostSharing()) return;

        CapacityChecker capacityChecker = new CapacityChecker(nodeRepository());

        List<Node> overcommittedHosts = capacityChecker.findOvercommittedHosts();
        if (overcommittedHosts.size() != 0) {
            log.log(Level.WARNING, String.format("%d nodes are overcommitted! [ %s ]",
                                                 overcommittedHosts.size(),
                                                 overcommittedHosts.stream().map(Node::hostname).collect(Collectors.joining(", "))));
        }
        metric.set("overcommittedHosts", overcommittedHosts.size(), null);

        Optional<CapacityChecker.HostFailurePath> failurePath = capacityChecker.worstCaseHostLossLeadingToFailure();
        if (failurePath.isPresent()) {
            int spareHostCapacity = failurePath.get().hostsCausingFailure.size() - 1;
            if (spareHostCapacity == 0) {
                Move move = findMitigatingMove(failurePath.get());
                if (moving(move)) {
                    // We succeeded or are in the process of taking a step to mitigate.
                    // Report with the assumption this will eventually succeed to avoid alerting before we're stuck
                    spareHostCapacity++;
                }
            }
            metric.set("spareHostCapacity", spareHostCapacity, null);
        }
    }

    private boolean moving(Move move) {
        if (move.isEmpty()) return false;
        if (move.node().allocation().get().membership().retired()) return true; // Move already in progress
        return move.execute(false, Agent.SpareCapacityMaintainer, deployer, metric, nodeRepository());
    }

    private Move findMitigatingMove(CapacityChecker.HostFailurePath failurePath) {
        Optional<Node> nodeWhichCantMove = failurePath.failureReason.tenant;
        if (nodeWhichCantMove.isEmpty()) return Move.empty();
        return moveTowardsSpareFor(nodeWhichCantMove.get());
    }

    private Move moveTowardsSpareFor(Node node) {
        System.out.println("Trying to find mitigation for " + node);
        NodeList allNodes = nodeRepository().list();
        // Allocation will assign the two most empty nodes as "spares", which will not be allocated on
        // unless needed for node failing. Our goal here is to make room on these spares for the given node
        HostCapacity hostCapacity = new HostCapacity(allNodes, nodeRepository().resourcesCalculator());
        Set<Node> spareHosts = hostCapacity.findSpareHosts(allNodes.hosts().satisfies(node.resources()).asList(), 2);
        List<Node> hosts = allNodes.hosts().except(spareHosts).asList();

        CapacitySolver capacitySolver = new CapacitySolver(hostCapacity);
        List<Move> shortestMitigation = null;
        for (Node spareHost : spareHosts) {
            List<Move> mitigation = capacitySolver.makeRoomFor(node, spareHost, hosts, List.of(), maxMoves);
            if (mitigation == null) continue;
            if (shortestMitigation == null || shortestMitigation.size() > mitigation.size())
                shortestMitigation = mitigation;
        }
        if (shortestMitigation == null || shortestMitigation.isEmpty()) return Move.empty();
        System.out.println("Shortest mitigation to create spare for " + node + ":\n  " + shortestMitigation);
        return shortestMitigation.get(0);
    }

    private static class CapacitySolver {

        private final HostCapacity hostCapacity;

        CapacitySolver(HostCapacity hostCapacity) {
            this.hostCapacity = hostCapacity;
        }

        /**
         * Finds the shortest sequence of moves which makes room for the given node on the given host,
         * assuming the given moves already made over the given hosts' current allocation.
         *
         * @param node the node to make room for
         * @param host the target host to make room on
         * @param hosts the hosts onto which we can move nodes
         * @param movesMade the moves already made in this scenario
         * @return the list of movesMade with the moves needed for this appended, in the order they should be performed,
         *         or null if no sequence could be found
         */
        List<Move> makeRoomFor(Node node, Node host, List<Node> hosts, List<Move> movesMade, int movesLeft) {
            if ( ! host.resources().satisfies(node.resources())) return null;
            NodeResources freeCapacity = freeCapacityWith(movesMade, host);
            if (freeCapacity.satisfies(node.resources())) return List.of();
            if (movesLeft == 0) return null;

            List<Move> shortest = null;
            for (var i = subsets(hostCapacity.allNodes().childrenOf(host), movesLeft); i.hasNext(); ) {
                List<Node> childrenToMove = i.next();
                if ( ! addResourcesOf(childrenToMove, freeCapacity).satisfies(node.resources())) continue;
                List<Move> moves = move(childrenToMove, host, hosts, movesMade, movesLeft);
                if (moves == null) continue;

                if (shortest == null || moves.size() < shortest.size())
                    shortest = moves;
            }
            if (shortest == null) return null;
            List<Move> total = append(movesMade, shortest);
            if (total.size() > movesLeft) return null;
            return total;
        }

        private List<Move> move(List<Node> nodes, Node host, List<Node> hosts, List<Move> movesMade, int movesLeft) {
            List<Move> moves = new ArrayList<>();
            for (Node childToMove : nodes) {
                List<Move> childMoves = move(childToMove, host, hosts, append(movesMade, moves), movesLeft - moves.size());
                if (childMoves == null) return null;
                moves.addAll(childMoves);
                if (moves.size() > movesLeft) return null;
            }
            return moves;
        }

        private List<Move> move(Node node, Node host, List<Node> hosts, List<Move> movesMade, int movesLeft) {
            List<Move> shortest = null;
            for (Node target : hosts) {
                if (target.equals(host)) continue;
                List<Move> childMoves = makeRoomFor(node, target, hosts, movesMade, movesLeft - 1);
                if (childMoves == null) continue;
                if (shortest == null || shortest.size() > childMoves.size() + 1) {
                    shortest = new ArrayList<>(childMoves);
                    shortest.add(new Move(node, host, target));
                }
            }
            return shortest;
        }

        private NodeResources addResourcesOf(List<Node> nodes, NodeResources resources) {
            for (Node node : nodes)
                resources = resources.add(node.resources());
            return resources;
        }

        private Iterator<List<Node>> subsets(NodeList nodes, int maxLength) {
            return new SubsetIterator(nodes.asList(), maxLength);
        }

        private List<Move> append(List<Move> a, List<Move> b) {
            List<Move> list = new ArrayList<>();
            list.addAll(a);
            list.addAll(b);
            return list;
        }

        private NodeResources freeCapacityWith(List<Move> moves, Node host) {
            NodeResources resources = hostCapacity.freeCapacityOf(host);
            for (Move move : moves) {
                if ( ! move.toHost().equals(host)) continue;
                resources = resources.subtract(move.node().resources());
            }
            for (Move move : moves) {
                if ( ! move.fromHost().equals(host)) continue;
                resources = resources.add(move.fromHost().resources());
            }
            return resources;
        }

    }

    private static class SubsetIterator implements Iterator<List<Node>> {

        private final List<Node> nodes;
        private final int maxLength;

        // A number whose binary representation determines which items of list we'll include
        private int i = 0; // first "previous" = 0 -> skip the empty set
        private List<Node> next = null;

        public SubsetIterator(List<Node> nodes, int maxLength) {
            this.nodes = new ArrayList<>(nodes.subList(0, Math.min(nodes.size(), 31)));
            this.maxLength = maxLength;
        }

        @Override
        public boolean hasNext() {
            if (next != null) return true;

            // find next
            while (++i < 1<<nodes.size()) {
                int ones = onesIn(i);
                if (ones > maxLength) continue;

                next = new ArrayList<>(ones);
                for (int position = 0; position < nodes.size(); position++) {
                    if (hasOneAtPosition(position, i))
                        next.add(nodes.get(position));
                }
                return true;
            }
            return false;
        }

        @Override
        public List<Node> next() {
            if ( ! hasNext()) throw new IllegalStateException("No more elements");
            var current = next;
            next = null;
            return current;
        }

        private boolean hasOneAtPosition(int position, int number) {
            return (number & (1 << position)) > 0;
        }

        private int onesIn(int number) {
            int ones = 0;
            for (int position = 0; Math.pow(2, position) <= number; position++) {
                if (hasOneAtPosition(position, number))
                    ones++;
            }
            return ones;
        }

    }

}
