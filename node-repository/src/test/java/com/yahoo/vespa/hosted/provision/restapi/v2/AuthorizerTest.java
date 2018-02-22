// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class AuthorizerTest {

    private Authorizer authorizer;
    private MockNodeRepository nodeRepository;

    @Before
    public void before() {
        nodeRepository = new MockNodeRepository(new MockCurator(), new MockNodeFlavors());
        authorizer = new Authorizer(SystemName.main, nodeRepository);
    }

    @Test
    public void authorization() {
        // Empty principal
        assertFalse(authorized("", ""));
        assertFalse(authorized("", "/"));

        // Node can only access its own resources
        assertFalse(authorized("node1", ""));
        assertFalse(authorized("node1", "/"));
        assertFalse(authorized("node1", "/nodes/v2/node"));
        assertFalse(authorized("node1", "/nodes/v2/node/"));
        assertFalse(authorized("node1", "/nodes/v2/node/node2"));
        assertFalse(authorized("node1", "/nodes/v2/state/dirty/"));
        assertFalse(authorized("node1", "/nodes/v2/state/dirty/node2"));
        assertFalse(authorized("node1", "/nodes/v2/acl/node2"));
        assertFalse(authorized("node1", "/nodes/v2/node/?parentHost=node2"));
        // Node resource always takes precedence over filter
        assertFalse(authorized("node1", "/nodes/v2/acl/node2?hostname=node1"));
        assertFalse(authorized("node1", "/nodes/v2/command/reboot/"));
        assertFalse(authorized("node1", "/nodes/v2/command/reboot/?hostname="));
        assertFalse(authorized("node1", "/nodes/v2/command/reboot/?hostname=node2"));
        assertTrue(authorized("node1", "/nodes/v2/node/node1"));
        assertTrue(authorized("node1", "/nodes/v2/state/dirty/node1"));
        assertTrue(authorized("node1", "/nodes/v2/acl/node1"));
        assertTrue(authorized("node1", "/nodes/v2/command/reboot?hostname=node1"));
        assertTrue(authorized("node1", "/nodes/v2/node/?parentHost=node1"));

        // Host node can access itself and its children
        assertFalse(authorized("dockerhost1.yahoo.com", "/nodes/v2/node/host5.yahoo.com"));
        assertFalse(authorized("dockerhost1.yahoo.com", "/nodes/v2/command/reboot?hostname=host5.yahoo.com"));
        assertTrue(authorized("dockerhost1.yahoo.com", "/nodes/v2/node/dockerhost1.yahoo.com"));
        assertTrue(authorized("dockerhost1.yahoo.com", "/nodes/v2/node/host4.yahoo.com"));
        assertTrue(authorized("dockerhost1.yahoo.com", "/nodes/v2/command/reboot?hostname=host4.yahoo.com"));

        // Trusted services can access everything in their own system
        assertFalse(authorized("vespa.vespa.cd.hosting", "/")); // Wrong system
        assertTrue(new Authorizer(SystemName.cd, nodeRepository).test(() -> "vespa.vespa.cd.hosting", uri("/")));
        assertTrue(authorized("vespa.vespa.hosting", "/"));
        assertTrue(authorized("vespa.vespa.hosting", "/nodes/v2/node/"));
        assertTrue(authorized("vespa.vespa.hosting", "/nodes/v2/node/node1"));
    }

    private boolean authorized(String principal, String path) {
        return authorizer.test(() -> principal, uri(path));
    }

    private static URI uri(String path) {
        return URI.create("http://localhost").resolve(path);
    }

}
