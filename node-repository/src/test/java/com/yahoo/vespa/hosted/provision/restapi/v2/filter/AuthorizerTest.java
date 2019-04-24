// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifierTest.ATHENZ_PROVIDER_HOSTNAME;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifierTest.CONFIG_SERVER_IDENTITY;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifierTest.CONTROLLER_IDENTITY;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifierTest.FILTER_CONFIG;
import static com.yahoo.vespa.hosted.provision.restapi.v2.filter.NodeIdentifierTest.TENANT_HOST_IDENTITY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class AuthorizerTest {

    private Authorizer authorizer;

    @Before
    public void before() {
        NodeFlavors flavors = new MockNodeFlavors();
        MockNodeRepository nodeRepository = new MockNodeRepository(new MockCurator(), flavors);
        authorizer = new Authorizer(nodeRepository, FILTER_CONFIG);

        Set<String> ipAddresses = Set.of("127.0.0.1", "::1");
        Flavor flavor = flavors.getFlavorOrThrow("default");
        List<Node> nodes = List.of(
                nodeRepository.createNode(
                        "host1", "host1", ipAddresses, Optional.empty(), flavor, NodeType.host),
                nodeRepository.createNode(
                        "child1-1", "child1-1", ipAddresses, Optional.of("host1"), flavor, NodeType.tenant),
                nodeRepository.createNode(
                        "child1-2", "child1-2", ipAddresses, Optional.of("host1"), flavor, NodeType.tenant),
                nodeRepository.createNode(
                        "host2", "host2", ipAddresses, Optional.empty(), flavor, NodeType.host),
                nodeRepository.createNode(
                        "child2-1", "child2-1", ipAddresses, Optional.of("host1.tld"), flavor, NodeType.tenant),
                nodeRepository.createNode(
                        "proxy1", "proxy1", ipAddresses, Optional.of("proxyhost1"), flavor, NodeType.proxy),
                nodeRepository.createNode(
                        "proxyhost1", "proxyhost1", ipAddresses, Optional.empty(), flavor, NodeType.proxyhost)
        );
        nodeRepository.addNodes(nodes);
    }

    @Test
    public void root_authorization() {
        assertFalse(authorizedTenantNode("", ""));
        assertFalse(authorizedTenantNode("", "/"));
        assertFalse(authorizedTenantNode("node1", ""));
        assertFalse(authorizedTenantNode("node1", "/"));
    }

    @Test
    public void nodes_authorization() {
        // Node can only access its own resources
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/node"));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/node/"));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/node/node2"));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/state/dirty/"));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/state/dirty/node2"));
        // Path traversal fails gracefully
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/node/."));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/node/.."));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/acl/node2"));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/node/?parentHost=node2"));
        // Node resource always takes precedence over filter
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/acl/node2?hostname=node1"));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/command/reboot/"));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/command/reboot/?hostname="));
        assertFalse(authorizedTenantNode("node1", "/nodes/v2/command/reboot/?hostname=node2"));
        assertTrue(authorizedTenantNode("node1", "/nodes/v2/node/node1"));
        assertTrue(authorizedTenantNode("node1", "/nodes/v2/state/dirty/node1"));
        assertTrue(authorizedTenantNode("node1", "/nodes/v2/acl/node1"));
        assertTrue(authorizedTenantNode("node1", "/nodes/v2/command/reboot?hostname=node1"));
        assertTrue(authorizedTenantNode("node1", "/nodes/v2/node/?parentHost=node1"));

        // Host node can access itself and its children
        assertFalse(authorizedTenantHostNode("host1", "/nodes/v2/node/child2-1"));
        assertFalse(authorizedTenantHostNode("host1", "/nodes/v2/command/reboot?hostname=child2-1"));
        assertTrue(authorizedTenantHostNode("host1", "/nodes/v2/node/host1"));
        assertTrue(authorizedTenantHostNode("host1", "/nodes/v2/node/child1-1"));
        assertTrue(authorizedTenantHostNode("host1", "/nodes/v2/command/reboot?hostname=child1-1"));
        assertTrue(authorizedTenantHostNode("host1", "/athenz/v1/provider/identity-document/tenant/host1"));
        assertTrue(authorizedTenantHostNode("host1", "/athenz/v1/provider/identity-document/node/child1-1"));

        // Trusted services can access everything in their own system
        assertTrue(authorizedController(CONTROLLER_IDENTITY, "/"));
        assertTrue(authorizedController(CONFIG_SERVER_IDENTITY, "/"));
        assertTrue(authorizedController(CONTROLLER_IDENTITY, "/nodes/v2/node/"));
        assertTrue(authorizedController(CONTROLLER_IDENTITY, "/nodes/v2/node/node1"));
        assertTrue(authorizedController(CONFIG_SERVER_IDENTITY, "/nodes/v2/node/node1"));
    }

    @Test
    public void orchestrator_authorization() {
        // Node can only access its own resources
        assertFalse(authorizedTenantNode("node1", "/orchestrator/v1/hosts"));
        assertFalse(authorizedTenantNode("node1", "/orchestrator/v1/hosts/"));
        assertFalse(authorizedTenantNode("node1", "/orchestrator/v1/hosts/node2"));
        assertFalse(authorizedTenantNode("node1", "/orchestrator/v1/hosts/node2/suspended"));

        // Node can suspend itself
        assertTrue(authorizedTenantNode("node1", "/orchestrator/v1/hosts/node1"));
        assertTrue(authorizedTenantNode("node1", "/orchestrator/v1/hosts/node1/suspended"));

        // Host node can suspend itself and its children
        assertFalse(authorizedTenantHostNode("host1", "/orchestrator/v1/hosts/child2-1/suspended"));
        assertFalse(authorizedTenantHostNode("host1", "/orchestrator/v1/suspensions/hosts/host1?hostname=child2-1"));
        // All given hostnames must be children
        assertFalse(authorizedTenantHostNode("host1", "/orchestrator/v1/suspensions/hosts/host1?hostname=child1-1&hostname=child2-1"));
        assertTrue(authorizedTenantHostNode("host1", "/orchestrator/v1/hosts/host1/suspended"));
        assertTrue(authorizedTenantHostNode("host1", "/orchestrator/v1/hosts/child1-1/suspended"));
        assertTrue(authorizedTenantHostNode("host1", "/orchestrator/v1/suspensions/hosts/host1?hostname=child1-1"));
        // Multiple children
        assertTrue(authorizedTenantHostNode("host1", "/orchestrator/v1/suspensions/hosts/host1?hostname=child1-1&hostname=child1-2"));
    }

    @Test
    public void flags_authorization() {
        // Tenant nodes cannot access flags resources
        assertFalse(authorizedTenantNode("node1", "/flags/v1/data"));
        assertFalse(authorizedTenantNode("node1", "/flags/v1/data/flagid"));
        assertFalse(authorizedTenantNode("node1", "/flags/v1/foo"));

        // Host node can access data
        assertTrue(authorizedTenantHostNode("host1", "/flags/v1/data"));
        assertFalse(authorizedTenantHostNode("host1", "/flags/v1/data/flagid"));
        assertFalse(authorizedTenantHostNode("host1", "/flags/v1/foo"));
        assertTrue(authorizedTenantHostNode("proxy1-host", "/flags/v1/data"));
        assertFalse(authorizedTenantHostNode("proxy1-host", "/flags/v1/data/flagid"));
        assertFalse(authorizedTenantHostNode("proxy1-host", "/flags/v1/foo"));
        assertTrue(authorizedController(CONFIG_SERVER_IDENTITY, "/flags/v1/data"));
        assertFalse(authorizedController(CONFIG_SERVER_IDENTITY, "/flags/v1/data/flagid"));
        assertFalse(authorizedController(CONFIG_SERVER_IDENTITY, "/flags/v1/foo"));

        // Controller can access everything
        assertTrue(authorizedController(CONTROLLER_IDENTITY, "/flags/v1/data"));
        assertTrue(authorizedController(CONTROLLER_IDENTITY, "/flags/v1/data/flagid"));
        assertTrue(authorizedController(CONTROLLER_IDENTITY, "/flags/v1/foo"));
    }

    @Test
    public void routing_authorization() {
        // Node of proxy or proxyhost type can access routing resource
        assertFalse(authorizedTenantNode("node1", "/routing/v1/status"));
        assertTrue(authorizedTenantNode("proxy1", "/routing/v1/status"));
        assertTrue(authorizedTenantNode("proxyhost1", "/routing/v1/status"));
    }

    @Test
    public void zts_allowed_for_athenz_provider_api() {
        assertTrue(authorizedLegacyNode(ATHENZ_PROVIDER_HOSTNAME, "/athenz/v1/provider/refresh"));
        assertTrue(authorizedLegacyNode(ATHENZ_PROVIDER_HOSTNAME, "/athenz/v1/provider/instance"));
    }

    private boolean authorizedTenantNode(String hostname, String path) {
        return authorized(NodePrincipal.withAthenzIdentity("vespa.vespa.tenant", hostname, List.of()), path);
    }

    private boolean authorizedTenantHostNode(String hostname, String path) {
        return authorized(NodePrincipal.withAthenzIdentity(TENANT_HOST_IDENTITY, hostname, List.of()), path);
    }

    private boolean authorizedLegacyNode(String hostname, String path) {
        return authorized(NodePrincipal.withLegacyIdentity(hostname, List.of()), path);
    }

    private boolean authorizedController(String controllerIdentity, String path) {
        return authorized(NodePrincipal.withAthenzIdentity(controllerIdentity, List.of()), path);
    }

    private boolean authorized(NodePrincipal principal, String path) {
        return authorizer.test(principal, uri(path));
    }

    private static URI uri(String path) {
        return URI.create("http://localhost").resolve(path);
    }

}
