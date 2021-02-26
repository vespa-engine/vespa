// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockReconfigurer;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ConfigServerReconfigurerTest {

    @Test
    public void maintain() {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        flagSource.withBooleanFlag(Flags.DYNAMIC_CONFIG_SERVER_PROVISIONING.id(), true);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Cloud.builder().dynamicProvisioning(true).build(),
                                                                                   SystemName.defaultSystem(),
                                                                                   Environment.defaultEnvironment(),
                                                                                   RegionName.defaultName()))
                                                                    .hostProvisioner(new MockHostProvisioner())
                                                                    .flagSource(flagSource)
                                                                    .build();
        MockReconfigurer reconfigurer = new MockReconfigurer();
        ConfigServerReconfigurer maintainer = new ConfigServerReconfigurer(tester.nodeRepository(), Duration.ofDays(1),
                                                                           new TestMetric(), reconfigurer);

        // Initially there are not enough config servers to trigger reconfiguration
        tester.makeConfigServers(2, 1, "default");
        maintainer.maintain();
        assertEquals("No change: Too few servers", List.of(), reconfigurer.servers());

        // Another is added, triggering reconfiguration
        NodeList configServer = tester.makeConfigServers(1, 3, "default");
        maintainer.maintain();
        List<ZooKeeperServer> configuredServers = List.of(new ZooKeeperServer(0, "cfg1"),
                                                          new ZooKeeperServer(1, "cfg2"),
                                                          new ZooKeeperServer(2, "cfg3"));
        assertEquals("Reconfigured", configuredServers, reconfigurer.servers());
        assertEquals(1, reconfigurer.reconfigurations());

        // A config server is deallocated, no longer enough active nodes to reconfigure
        tester.nodeRepository().nodes().deallocate(configServer.first().get(), Agent.system, this.getClass().getSimpleName());
        maintainer.maintain();
        assertEquals("No change: Too few active servers", configuredServers, reconfigurer.servers());
        assertEquals(1, reconfigurer.reconfigurations());
    }

    private static class MockHostProvisioner implements HostProvisioner {

        @Override
        public List<ProvisionedHost> provisionHosts(List<Integer> provisionIndexes, NodeResources resources, ApplicationId applicationId, Version osVersion, HostSharing sharing) {
            return List.of();
        }

        @Override
        public List<Node> provision(Node host, Set<Node> children) throws FatalProvisioningException {
            return List.of();
        }

        @Override
        public void deprovision(Node host) {
        }

    }

}
