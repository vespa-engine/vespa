// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.assimilate;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Test;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author vegard
 */
public class PopulateClientTest {

    final static String servicesXmlFilename = "src/test/resources/services.xml";
    final static String hostsXmlFilename = "src/test/resources/hosts.xml";

    final List<String> hostnames = Arrays.asList("hostname1", "hostname2", "hostname3", "hostname4", "hostname5", "hostname6");
    final List<String> clusterTypes = Arrays.asList("container", "container", "content", "content", "content", "content");
    final List<String> clusterIds = Arrays.asList("default", "default", "default", "default", "mycontent", "mycontent");
    final List<Integer> nodeIndices = Arrays.asList(0, 1, 99, 42, 0, 1);

    final String tenantId = "vegard";
    final String applicationId = "killer-app";
    final String instanceId = "default";

    final Map<String, String> flavorSpec = ImmutableMap.of(
            "container.default", "vanilla",
            "content.default", "strawberry",
            "content.mycontent", "chocolate"
    );

    NodeFlavors flavors = FlavorConfigBuilder.createDummies(flavorSpec.values().stream().collect(Collectors.toList()).toArray(new String[flavorSpec.size()]));

    @Test
    public void testCorrectDataIsWrittenToZooKeeper() {
        Curator curator = new MockCurator();
        CuratorDatabaseClient curatorDatabaseClient = new CuratorDatabaseClient(flavors, curator, 
                                                                                Clock.systemUTC(), Zone.defaultZone());

        PopulateClient populateClient = new PopulateClient(curator, flavors, tenantId, applicationId, instanceId, 
                                                           servicesXmlFilename, hostsXmlFilename, flavorSpec, false);
        populateClient.populate(PopulateClient.CONTAINER_CLUSTER_TYPE);
        populateClient.populate(PopulateClient.CONTENT_CLUSTER_TYPE);

        List<Node> nodes = curatorDatabaseClient.getNodes(ApplicationId.from(
                TenantName.from(tenantId),
                ApplicationName.from(applicationId),
                InstanceName.from(instanceId)));

        assertThat("Zookeeper is populated", nodes.size(), is(hostnames.size()));

        nodes.stream().forEach(node -> {
            assertThat("Node has allocation", node.allocation(), notNullValue());

            final Allocation allocation = node.allocation().get();
            assertThat("Application id must match", allocation.owner().application().toString(), is(applicationId));
            assertThat("Tenant id must match", allocation.owner().tenant().toString(), is(tenantId));
            assertThat("Instance id must match", allocation.owner().instance().toString(), is(instanceId));

            final int index = hostnames.indexOf(node.hostname());
            assertThat("Hostname must be one the hostnames", index, is(not(-1)));

            final String clusterType = allocation.membership().cluster().type().name();
            assertThat("Cluster type must match", clusterType, is(clusterTypes.get(index)));

            final String clusterId = allocation.membership().cluster().id().value();
            assertThat("Cluster id must match", clusterId, is(clusterIds.get(index)));

            assertThat("Flavor must match", node.flavor().name(), is(flavorSpec.get(clusterType + "." + clusterId)));
            assertThat("Node index must match", node.allocation().get().membership().index(), is(nodeIndices.get(index)));
        });
    }

    @Test(expected = RuntimeException.class)
    public void testNotSpecifyingAFlavorThrowsException() {
        Map<String, String> myFlavorSpec = ImmutableMap.of(
                "container.default", "vanilla",
                "content.default", "strawberry"
                // missing content.mycontent
        );

        new PopulateClient(new MockCurator(), flavors, tenantId, applicationId, instanceId, servicesXmlFilename, hostsXmlFilename, myFlavorSpec, false);
    }

}
