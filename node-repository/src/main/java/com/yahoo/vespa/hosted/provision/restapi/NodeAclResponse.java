// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.NodeAcl;

import java.util.List;
import java.util.Set;

/**
 * @author mpolden
 */
public class NodeAclResponse extends SlimeJsonResponse {

    private static final String CHILDREN_REQUEST_PROPERTY = "children";

    private final NodeRepository nodeRepository;
    private final boolean aclsForChildren;

    public NodeAclResponse(HttpRequest request, NodeRepository nodeRepository, String hostname) {
        this.nodeRepository = nodeRepository;
        this.aclsForChildren = request.getBooleanProperty(CHILDREN_REQUEST_PROPERTY); // This is always true?

        Cursor root = slime.setObject();
        toSlime(hostname, root);
    }

    private void toSlime(String hostname, Cursor object) {
        Node node = nodeRepository.nodes().node(hostname)
                .orElseThrow(() -> new NotFoundException("No node with hostname '" + hostname + "'"));

        List<NodeAcl> acls = aclsForChildren ? nodeRepository.getChildAcls(node) :
                                               List.of(node.acl(nodeRepository.nodes().list(), nodeRepository.loadBalancers()));

        Cursor trustedNodesArray = object.setArray("trustedNodes");
        acls.forEach(nodeAcl -> toSlime(nodeAcl, trustedNodesArray));

        Cursor trustedNetworksArray = object.setArray("trustedNetworks");
        acls.forEach(nodeAcl -> toSlime(nodeAcl.trustedNetworks(), nodeAcl.node(), trustedNetworksArray));

        Cursor trustedPortsArray = object.setArray("trustedPorts");
        acls.forEach(nodeAcl -> toSlime(nodeAcl.trustedPorts(), nodeAcl, trustedPortsArray));

        Cursor trustedUdpPortsArray = object.setArray("trustedUdpPorts");
        acls.forEach(nodeAcl -> toSlime(nodeAcl.trustedUdpPorts(), nodeAcl, trustedUdpPortsArray));
    }

    private void toSlime(NodeAcl nodeAcl, Cursor array) {
        nodeAcl.trustedNodes().forEach(node -> node.ipAddresses().forEach(ipAddress -> {
            Cursor object = array.addObject();
            object.setString("hostname", node.hostname());
            object.setString("type", node.type().name());
            object.setString("ipAddress", ipAddress);
            if (!node.ports().isEmpty()) {
                Cursor portsArray = object.setArray("ports");
                node.ports().stream().sorted().forEach(portsArray::addLong);
            }
            object.setString("trustedBy", nodeAcl.node().hostname());
        }));
    }

    private void toSlime(Set<String> trustedNetworks, Node trustedby, Cursor array) {
        trustedNetworks.forEach(network -> {
            Cursor object = array.addObject();
            object.setString("network", network);
            object.setString("trustedBy", trustedby.hostname());
        });
    }

    private void toSlime(Set<Integer> trustedPorts, NodeAcl trustedBy, Cursor array) {
        trustedPorts.forEach(port -> {
            Cursor object = array.addObject();
            object.setLong("port", port);
            object.setString("trustedBy", trustedBy.node().hostname());
        });
    }
}
