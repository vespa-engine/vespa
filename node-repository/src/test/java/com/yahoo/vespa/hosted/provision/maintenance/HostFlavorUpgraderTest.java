package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner.Behaviour;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class HostFlavorUpgraderTest {

    @Test
    public void maintain() {
        String flavor0 = "host";
        String flavor1 = "host2";
        NodeFlavors flavors = FlavorConfigBuilder.createDummies(flavor0, flavor1);
        MockHostProvisioner hostProvisioner = new MockHostProvisioner(flavors.getFlavors());
        ProvisioningTester tester = new ProvisioningTester.Builder().dynamicProvisioning()
                                                                    .flavors(flavors.getFlavors())
                                                                    .hostProvisioner(hostProvisioner)
                                                                    .build();
        ApplicationId app = ProvisioningTester.applicationId();
        NodeResources resources = new NodeResources(4, 8, 100, 1,
                                                    NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote);
        ClusterSpec spec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("c1")).vespaVersion("1").build();
        Capacity capacity = Capacity.from(new ClusterResources(2, 1, resources));
        Map<ApplicationId, MockDeployer.ApplicationContext> applications = Map.of(app, new MockDeployer.ApplicationContext(app, spec, capacity));
        MockDeployer deployer = new MockDeployer(tester.provisioner(), tester.clock(), applications);
        HostFlavorUpgrader upgrader = new HostFlavorUpgrader(tester.nodeRepository(), Duration.ofDays(1),
                                                             new TestMetric(), deployer, hostProvisioner);

        // Provision hosts and deploy application
        tester.makeReadyNodes(2, flavor0, NodeType.host);
        tester.activateTenantHosts();
        tester.deploy(app, spec, capacity);
        Node host = tester.nodeRepository().nodes().list().hosts().first().get();
        assertEquals(flavor0, host.flavor().name());

        // Nothing to upgrade initially
        assertEquals(1, upgrader.maintain());
        assertEquals(NodeList.of(), tester.nodeRepository().nodes().list()
                                          .matching(h -> h.status().wantToUpgradeFlavor()));

        // Mark flavor as upgradable, but fail all provisioning requests
        hostProvisioner.addUpgradableFlavor(flavor0)
                       .with(Behaviour.failProvisionRequest);
        assertEquals(1, upgrader.maintain());
        assertEquals(NodeList.of(),
                     tester.nodeRepository().nodes().list()
                           .matching(node -> node.status().wantToUpgradeFlavor() || node.status().wantToRetire()),
                     "No hosts marked for upgrade or retirement");

        // First provision request fails, but second succeeds and a replacement host starts provisioning
        hostProvisioner.with(Behaviour.failProvisionRequest, 1);
        assertEquals(1, upgrader.maintain());
        NodeList nodes = tester.nodeRepository().nodes().list();
        NodeList upgradingFlavor = nodes.matching(node -> node.status().wantToRetire() &&
                                                          node.status().wantToUpgradeFlavor());
        assertEquals(1, upgradingFlavor.size());
        assertEquals(1, nodes.state(Node.State.provisioned).size());

        // No more upgrades are started while host is retiring
        assertEquals(1, upgrader.maintain());
        assertEquals(upgradingFlavor, tester.nodeRepository().nodes().list()
                                            .matching(node -> node.status().wantToUpgradeFlavor()));
    }

}
