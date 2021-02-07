// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.NodeAcl;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class LoadBalancers {

    private final CuratorDatabaseClient db;
    private final NodeRepository nodeRepository;

    public LoadBalancers(CuratorDatabaseClient db, NodeRepository nodeRepository) {
        this.db = db;
        this.nodeRepository = nodeRepository;
    }

    /** Returns a filterable list of all load balancers in this repository */
    public LoadBalancerList list() {
        return list((ignored) -> true);
    }

    /** Returns a filterable list of load balancers belonging to given application */
    public LoadBalancerList list(ApplicationId application) {
        return list((id) -> id.application().equals(application));
    }

    private LoadBalancerList list(Predicate<LoadBalancerId> predicate) {
        return LoadBalancerList.copyOf(db.readLoadBalancers(predicate).values());
    }

    /**
     * Returns the ACL for the node (trusted nodes, networks and ports)
     */
    private NodeAcl getNodeAcl(Node node, NodeList candidates) {
        Set<Node> trustedNodes = new TreeSet<>(Comparator.comparing(Node::hostname));
        Set<Integer> trustedPorts = new LinkedHashSet<>();
        Set<String> trustedNetworks = new LinkedHashSet<>();

        // For all cases below, trust:
        // - SSH: If the Docker host has one container, and it is using the Docker host's network namespace,
        //   opening up SSH to the Docker host is done here as a trusted port. For simplicity all nodes have
        //   SSH opened (which is safe for 2 reasons: SSH daemon is not run inside containers, and NPT networks
        //   will (should) not forward port 22 traffic to container).
        // - parent host (for health checks and metrics)
        // - nodes in same application
        // - load balancers allocated to application
        trustedPorts.add(22);
        candidates.parentOf(node).ifPresent(trustedNodes::add);
        node.allocation().ifPresent(allocation -> {
            trustedNodes.addAll(candidates.owner(allocation.owner()).asList());
            list(allocation.owner()).asList()
                                    .stream()
                                    .map(LoadBalancer::instance)
                                    .map(LoadBalancerInstance::networks)
                                    .forEach(trustedNetworks::addAll);
        });

        switch (node.type()) {
            case tenant:
                // Tenant nodes in other states than ready, trust:
                // - config servers
                // - proxy nodes
                // - parents of the nodes in the same application: If some of the nodes are on a different IP versions
                //   or only a subset of them are dual-stacked, the communication between the nodes may be NATed
                //   with via parent's IP address.
                trustedNodes.addAll(candidates.nodeType(NodeType.config).asList());
                trustedNodes.addAll(candidates.nodeType(NodeType.proxy).asList());
                node.allocation().ifPresent(allocation ->
                                                    trustedNodes.addAll(candidates.parentsOf(candidates.owner(allocation.owner())).asList()));

                if (node.state() == Node.State.ready) {
                    // Tenant nodes in state ready, trust:
                    // - All tenant nodes in zone. When a ready node is allocated to a an application there's a brief
                    //   window where current ACLs have not yet been applied on the node. To avoid service disruption
                    //   during this window, ready tenant nodes trust all other tenant nodes.
                    trustedNodes.addAll(candidates.nodeType(NodeType.tenant).asList());
                }
                break;

            case config:
                // Config servers trust:
                // - all nodes
                // - port 4443 from the world
                trustedNodes.addAll(candidates.asList());
                trustedPorts.add(4443);
                break;

            case proxy:
                // Proxy nodes trust:
                // - config servers
                // - all connections from the world on 4080 (insecure tb removed), and 4443
                trustedNodes.addAll(candidates.nodeType(NodeType.config).asList());
                trustedPorts.add(443);
                trustedPorts.add(4080);
                trustedPorts.add(4443);
                break;

            case controller:
                // Controllers:
                // - port 4443 (HTTPS + Athenz) from the world
                // - port 443 (HTTPS + Okta) from the world
                // - port 80 (HTTP) from the world - for redirect to HTTPS/443 only
                trustedPorts.add(4443);
                trustedPorts.add(443);
                trustedPorts.add(80);
                break;

            default:
                throw new IllegalArgumentException("Don't know how to create ACL for " + node +
                                                   " of type " + node.type());
        }

        return new NodeAcl(node, trustedNodes, trustedNetworks, trustedPorts);
    }

    /**
     * Creates a list of node ACLs which identify which nodes the given node should trust
     *
     * @param node Node for which to generate ACLs
     * @param children Return ACLs for the children of the given node (e.g. containers on a Docker host)
     * @return List of node ACLs
     */
    public List<NodeAcl> getNodeAcls(Node node, boolean children) {
        NodeList candidates = nodeRepository.list();
        if (children) {
            return candidates.childrenOf(node).asList().stream()
                             .map(childNode -> getNodeAcl(childNode, candidates))
                             .collect(Collectors.toUnmodifiableList());
        }
        return List.of(getNodeAcl(node, candidates));
    }

}
