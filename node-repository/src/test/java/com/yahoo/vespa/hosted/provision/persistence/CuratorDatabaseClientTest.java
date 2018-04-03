// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
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
public class CuratorDatabaseClientTest {

    private final Curator curator = new MockCurator();
    private final CuratorDatabaseClient zkClient = new CuratorDatabaseClient(
            FlavorConfigBuilder.createDummies("default"), curator, Clock.systemUTC(), Zone.defaultZone(), true);

    @Test
    public void can_read_stored_host_information() throws Exception {
        String zkline = "{\"hostname\":\"host1\",\"ipAddresses\":[\"127.0.0.1\"],\"openStackId\":\"7951bb9d-3989-4a60-a21c-13690637c8ea\",\"flavor\":\"default\",\"created\":1421054425159, \"type\":\"host\"}";
        curator.framework().create().creatingParentsIfNeeded().forPath("/provision/v1/ready/host1", zkline.getBytes());

        List<Node> allocatedNodes = zkClient.getNodes(Node.State.ready);
        assertEquals(1, allocatedNodes.size());
        assertEquals(NodeType.host, allocatedNodes.get(0).type());
    }

    @Test
    public void locks_can_be_acquired_and_released() {
        ApplicationId app = ApplicationId.from(TenantName.from("testTenant"), ApplicationName.from("testApp"), InstanceName.from("testInstance"));

        try (Lock mutex1 = zkClient.lock(app)) {
            mutex1.toString(); // reference to avoid warning
            throw new RuntimeException();
        }
        catch (RuntimeException expected) {
        }

        try (Lock mutex2 = zkClient.lock(app)) {
            mutex2.toString(); // reference to avoid warning
        }

        try (Lock mutex3 = zkClient.lock(app)) {
            mutex3.toString(); // reference to avoid warning
        }

    }

 }
