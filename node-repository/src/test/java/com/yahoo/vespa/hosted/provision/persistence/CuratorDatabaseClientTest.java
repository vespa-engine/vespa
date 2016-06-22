// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Test;

import java.time.Clock;
import java.util.List;
import static org.junit.Assert.assertEquals;

/**
 * @author Oyvind Gronnesby
 */
public class CuratorDatabaseClientTest {

    private Curator curator = new MockCurator();
    private CuratorDatabaseClient zkClient = new CuratorDatabaseClient(FlavorConfigBuilder.createDummies("default"), curator, Clock.systemUTC());

    @Test
    public void ensure_can_read_stored_host_with_instance_information_no_type() throws Exception {
        String zkline = "{\"hostname\":\"oxy-oxygen-0a4ae4f1.corp.bf1.yahoo.com\",\"openStackId\":\"7951bb9d-3989-4a60-a21c-13690637c8ea\",\"configuration\":{\"flavor\":\"default\"},\"created\":1421054425159,\"allocated\":1421057746687,\"instance\":{\"tenantId\":\"by_mortent\",\"applicationId\":\"music\",\"instanceId\":\"default\",\"serviceId\":\"container/default/0/0\"}}";

        curator.framework().create().creatingParentsIfNeeded().forPath("/provision/v1/allocated/oxy-oxygen-0a4ae4f1.corp.bf1.yahoo.com", zkline.getBytes());

        List<Node> allocatedNodes = zkClient.getNodes(Node.State.active);
        assertEquals(1, allocatedNodes.size());
        assertEquals("container/default/0/0", allocatedNodes.get(0).allocation().get().membership().stringValue());
        assertEquals(Node.Type.tenant, allocatedNodes.get(0).type());
    }

    @Test
    public void ensure_can_read_stored_host_information() throws Exception {
        String zkline = "{\"hostname\":\"oxy-oxygen-0a4ae4f1.corp.bf1.yahoo.com\",\"openStackId\":\"7951bb9d-3989-4a60-a21c-13690637c8ea\",\"configuration\":{\"flavor\":\"default\"},\"created\":1421054425159, \"type\":\"host\"}";
        curator.framework().create().creatingParentsIfNeeded().forPath("/provision/v1/ready/oxy-oxygen-0a4ae4f1.corp.bf1.yahoo.com", zkline.getBytes());

        List<Node> allocatedNodes = zkClient.getNodes(Node.State.ready);
        assertEquals(1, allocatedNodes.size());
        assertEquals(Node.Type.host, allocatedNodes.get(0).type());
    }

    /** Test that locks can be acquired and released */
    @Test
    public void testLocking() {
        ApplicationId app = ApplicationId.from(TenantName.from("testTenant"), ApplicationName.from("testApp"), InstanceName.from("testInstance"));

        try (CuratorMutex mutex1 = zkClient.lock(app)) {
            mutex1.toString(); // reference to avoid warning
            throw new RuntimeException();
        }
        catch (RuntimeException expected) {
        }

        try (CuratorMutex mutex2 = zkClient.lock(app)) {
            mutex2.toString(); // reference to avoid warning
        }

        try (CuratorMutex mutex3 = zkClient.lock(app)) {
            mutex3.toString(); // reference to avoid warning
        }

    }

 }
