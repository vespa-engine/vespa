// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import org.junit.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class CuratorDbTest {

    private final Curator curator = new MockCurator();
    private final CuratorDb zkClient = new CuratorDb(
            FlavorConfigBuilder.createDummies("default"), curator, Clock.systemUTC(), true, 1000);

    @Test
    public void can_read_stored_host_information() throws Exception {
        String zkline = "{\"hostname\":\"host1\",\"state\":\"ready\",\"ipAddresses\":[\"127.0.0.1\"],\"additionalIpAddresses\":[\"127.0.0.2\"],\"openStackId\":\"7951bb9d-3989-4a60-a21c-13690637c8ea\",\"flavor\":\"default\",\"created\":1421054425159, \"type\":\"host\"}";
        curator.framework().create().creatingParentsIfNeeded().forPath("/provision/v1/nodes/host1", zkline.getBytes());

        List<Node> allocatedNodes = zkClient.readNodes();
        assertEquals(1, allocatedNodes.size());
        assertEquals(NodeType.host, allocatedNodes.get(0).type());
    }

    @Test
    public void locks_can_be_acquired_and_released() {
        ApplicationId app = ApplicationId.from(TenantName.from("testTenant"), ApplicationName.from("testApp"), InstanceName.from("testInstance"));

        try (var ignored = zkClient.lock(app)) {
            throw new RuntimeException();
        }
        catch (RuntimeException expected) {
        }

        try (var ignored = zkClient.lock(app)) {
        }

        try (var ignored = zkClient.lock(app)) {
        }

    }

 }
