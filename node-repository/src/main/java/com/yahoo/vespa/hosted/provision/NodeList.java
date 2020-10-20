// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;

/**
 * A filterable node list. The result of a filter operation is immutable.
 *
 * @author bratseth
 * @author mpolden
 */
public class NodeList extends AbstractFilteringList<Node, NodeList> {

    protected NodeList(List<Node> nodes, boolean negate) {
        super(nodes, negate, NodeList::new);
    }

    /** Returns the subset of nodes which are retired */
    public NodeList retired() {
        return matching(node -> node.allocation().isPresent() && node.allocation().get().membership().retired());
    }

    /** Returns the subset of nodes that are being deprovisioned */
    public NodeList deprovisioning() {
        return matching(node -> node.status().wantToRetire() && node.status().wantToDeprovision());
    }

    /** Returns the subset of nodes which are removable */
    public NodeList removable() {
        return matching(node -> node.allocation().isPresent() && node.allocation().get().isRemovable());
    }

    /** Returns the subset of nodes having exactly the given resources */
    public NodeList resources(NodeResources resources) { return matching(node -> node.resources().equals(resources)); }

    /** Returns the subset of nodes which satisfy the given resources */
    public NodeList satisfies(NodeResources resources) { return matching(node -> node.resources().satisfies(resources)); }

    /** Returns the subset of nodes of the given flavor */
    public NodeList flavor(String flavor) {
        return matching(node -> node.flavor().name().equals(flavor));
    }

    /** Returns the subset of nodes not in the given collection */
    public NodeList except(Collection<Node> nodes) {
        return matching(node -> ! nodes.contains(node));
    }

    /** Returns the subset of nodes assigned to the given cluster type */
    public NodeList type(ClusterSpec.Type type) {
        return matching(node -> node.allocation().isPresent() && node.allocation().get().membership().cluster().type().equals(type));
    }

    /** Returns the subset of nodes that run containers */
    public NodeList container() {
        return matching(node -> node.allocation().isPresent() && node.allocation().get().membership().cluster().type().isContainer());
    }

    /** Returns the subset of nodes that are currently changing their Vespa version */
    public NodeList changingVersion() {
        return matching(node -> node.status().vespaVersion().isPresent() &&
                                node.allocation().isPresent() &&
                                !node.status().vespaVersion().get().equals(node.allocation().get().membership().cluster().vespaVersion()));
    }

    /** Returns the subset of nodes that are currently changing their OS version to given version */
    public NodeList changingOsVersionTo(Version version) {
        return matching(node -> node.status().osVersion().changingTo(version));
    }

    /** Returns the subset of nodes that are currently changing their OS version */
    public NodeList changingOsVersion() {
        return matching(node -> node.status().osVersion().changing());
    }

    /** Returns a copy of this sorted by current OS version (lowest to highest) */
    public NodeList byIncreasingOsVersion() {
        return sortedBy(Comparator.comparing(node -> node.status()
                                                         .osVersion()
                                                         .current()
                                                         .orElse(Version.emptyVersion)));
    }

    /** Returns the subset of nodes that are currently on a lower version than the given version */
    public NodeList osVersionIsBefore(Version version) {
        return matching(node -> node.status().osVersion().isBefore(version));
    }

    /** Returns the subset of nodes that are currently on the given OS version */
    public NodeList onOsVersion(Version version) {
        return matching(node -> node.status().osVersion().matches(version));
    }

    /** Returns the subset of nodes assigned to the given cluster */
    public NodeList cluster(ClusterSpec.Id cluster) {
        return matching(node -> node.allocation().isPresent() && node.allocation().get().membership().cluster().id().equals(cluster));
    }

    /** Returns the subset of nodes owned by the given application */
    public NodeList owner(ApplicationId application) {
        return matching(node -> node.allocation().map(a -> a.owner().equals(application)).orElse(false));
    }

    /** Returns the subset of nodes matching the given node type(s) */
    public NodeList nodeType(NodeType first, NodeType... rest) {
        EnumSet<NodeType> nodeTypes = EnumSet.of(first, rest);
        return matching(node -> nodeTypes.contains(node.type()));
    }

    /** Returns the subset of nodes of the host type */
    public NodeList hosts() {
        return matching(node -> node.type() == NodeType.host);
    }

    /** Returns the subset of nodes that are parents */
    public NodeList parents() {
        return matching(n -> n.parentHostname().isEmpty());
    }

    /** Returns the child nodes of the given parent node */
    public NodeList childrenOf(String hostname) {
        return matching(n -> n.parentHostname().map(hostname::equals).orElse(false));
    }

    public NodeList childrenOf(Node parent) {
        return childrenOf(parent.hostname());
    }

    /** Returns the subset of nodes that are in any of the given state(s) */
    public NodeList state(Node.State first, Node.State... rest) {
        return state(EnumSet.of(first, rest));
    }

    /** Returns the subset of nodes that are in any of the given state(s) */
    public NodeList state(Collection<Node.State> nodeStates) {
        return matching(node -> nodeStates.contains(node.state()));
    }

    /** Returns the subset of nodes which wantToRetire set true */
    public NodeList wantToRetire() {
        return matching((node -> node.status().wantToRetire()));
    }

    /** Returns the parent nodes of the given child nodes */
    public NodeList parentsOf(Collection<Node> children) {
        return children.stream()
                       .map(this::parentOf)
                       .filter(Optional::isPresent)
                       .flatMap(Optional::stream)
                       .collect(collectingAndThen(Collectors.toList(), NodeList::copyOf));
    }

    public NodeList group(int index) {
        return matching(n -> ( n.allocation().isPresent() &&
                               n.allocation().get().membership().cluster().group().equals(Optional.of(ClusterSpec.Group.from(index)))));
    }

    /** Returns the parent node of the given child node */
    public Optional<Node> parentOf(Node child) {
        return child.parentHostname()
                    .flatMap(parentHostname -> stream().filter(node -> node.hostname().equals(parentHostname))
                                                       .findFirst());
    }

    public ClusterResources toResources() {
        if (isEmpty()) return new ClusterResources(0, 0, NodeResources.unspecified());
        return new ClusterResources(size(),
                                    (int)stream().distinct().count(),
                                    first().get().resources());
    }

    /** Returns the nodes of this as a stream */
    public Stream<Node> stream() { return asList().stream(); }

    public static NodeList copyOf(List<Node> nodes) {
        return new NodeList(nodes, false);
    }

    @Override
    public String toString() {
        return asList().toString();
    }

}
