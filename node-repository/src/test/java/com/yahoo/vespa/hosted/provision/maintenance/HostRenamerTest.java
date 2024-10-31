package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class HostRenamerTest {

    @Test
    public void rename() {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flagSource(flagSource)
                                                                    .build();
        Supplier<NodeList> list = () -> tester.nodeRepository().nodes().list().not().state(Node.State.deprovisioned);
        HostRenamer renamer = new HostRenamer(tester.nodeRepository(), Duration.ofDays(1), new MockMetric());
        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ApplicationId app2 = ProvisioningTester.applicationId("app2");
        List<ApplicationId> applications = List.of(app1, app2);
        int hostCount = applications.size() + 1;
        provisionHosts(hostCount, tester, "legacy.example.com");

        // Deploy two apps that share hosts
        deploy(app1, tester);
        deploy(app2, tester);

        // Nothing happens when flag is unset
        renamer.maintain();
        assertEquals(0, list.get().retiring().size(), "No hosts to rename when feature flag is unset");

        // Rename hosts
        flagSource.withStringFlag(Flags.HOSTNAME_SCHEME.id(), "standard");
        for (int i = 0; i < hostCount; i++) {
            renamer.maintain();
            NodeList allNodes = list.get();
            for (var app : applications) {
                assertEquals(1, allNodes.owner(app).retiring().size(), "One node per app is retired at a time");
                replaceNodes(app, tester);
            }
            replaceHosts(allNodes.nodeType(NodeType.host).deprovisioning(), tester);
        }

        // Nothing more to do
        renamer.maintain();
        assertEquals(0, list.get().retiring().size(), "No more hosts to rename");
    }

    @Test
    public void renameGrouped() {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                .flagSource(flagSource)
                .build();
        Supplier<NodeList> list = () -> tester.nodeRepository().nodes().list().not().state(Node.State.deprovisioned);
        HostRenamer renamer = new HostRenamer(tester.nodeRepository(), Duration.ofDays(1), new MockMetric());

        ApplicationId groupedApp = ProvisioningTester.applicationId("groupedApp");
        int hostCount = 4;
        provisionHosts(hostCount, tester, "legacy.example.com");

        deployGroupedApp(groupedApp, tester);

        // Nothing happens when flag is unset
        renamer.maintain();
        assertEquals(0, list.get().retiring().size(), "No hosts to rename when feature flag is unset");

        // Rename hosts
        flagSource.withStringFlag(Flags.HOSTNAME_SCHEME.id(), "standard");
        renamer.maintain();

        assertEquals(2, list.get().owner(groupedApp).retiring().size(), "One node per group is retired at a time");
        List<Node> retiringNodes = list.get().owner(groupedApp).retiring().asList();
        assertNotEquals(
                "Retiring nodes are from different groups",
                retiringNodes.get(0).allocation().get().membership().cluster().group(),
                retiringNodes.get(1).allocation().get().membership().cluster().group()
        );
        assertEquals(2, list.get().hosts().retiring().size(), "Two hosts should be retired");
    }

    private void replaceHosts(NodeList hosts, ProvisioningTester tester) {
        for (var host : hosts) {
            if (!host.status().wantToRetire()) throw new IllegalArgumentException(host + " is not requested to retire");
            tester.nodeRepository().nodes().park(host.hostname(), true, Agent.system, getClass().getSimpleName());
            tester.nodeRepository().nodes().removeRecursively(host.hostname());
            provisionHosts(1, tester, "vespa-cloud.net");
        }
    }

    private void replaceNodes(ApplicationId application, ProvisioningTester tester) {
        // Deploy to retire nodes
        deploy(application, tester);
        NodeList retired = tester.nodeRepository().nodes().list().owner(application).retired();
        assertFalse("At least one node is retired", retired.isEmpty());
        tester.nodeRepository().nodes().setRemovable(retired, false);

        // Redeploy to deactivate removable nodes and allocate new ones
        deploy(application, tester);
        tester.nodeRepository().nodes().list(Node.State.inactive).owner(application)
              .forEach(node -> tester.nodeRepository().nodes().removeRecursively(node, true));
    }

    private void deploy(ApplicationId application, ProvisioningTester tester) {
        ClusterSpec contentSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content1")).vespaVersion("7").build();
        Capacity capacity = Capacity.from(new ClusterResources(2, 1, new NodeResources(2, 8, 50, 1)));
        tester.deploy(application, contentSpec, capacity);
    }

    private void deployGroupedApp(ApplicationId application, ProvisioningTester tester) {
        ClusterSpec group0Spec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content1")).vespaVersion("7").build();
        Capacity capacity = Capacity.from(new ClusterResources(4, 2, new NodeResources(2, 8, 50, 1)));
        tester.deploy(application, group0Spec, capacity);
    }

    private void provisionHosts(int count, ProvisioningTester tester, String domain) {
        List<Node> nodes = tester.makeProvisionedNodes(count, (index) -> "host-" + index + "." + domain, new Flavor(new NodeResources(32, 128, 1024, 10)),
                                                       Optional.empty(), NodeType.host, 10, false);
        nodes = tester.nodeRepository().nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        tester.move(Node.State.ready, nodes);
        tester.activateTenantHosts();
    }

}
