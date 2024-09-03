package com.yahoo.vespa.hosted.provision.maintenance;

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
        Supplier<NodeList> retiring = () -> tester.nodeRepository().nodes().list().not().state(Node.State.deprovisioned).retiring();
        int hostCount = 3;
        provisionHosts(hostCount, tester, "legacy.example.com");

        HostRenamer renamer = new HostRenamer(tester.nodeRepository(), Duration.ofDays(1), new MockMetric());
        renamer.maintain();
        assertEquals(0, retiring.get().size(), "No hosts to rename when feature flag is unset");

        // Set flag
        flagSource.withStringFlag(Flags.HOSTNAME_SCHEME.id(), "standard");
        for (int i = 0; i < hostCount; i++) {
            renamer.maintain();
            NodeList retiringNodes = retiring.get();
            assertEquals(1, retiringNodes.size(), "Hosts are retired 1-by-1");
            replace(retiringNodes.first().get(), tester);
        }

        // Nothing more to do
        renamer.maintain();
        assertEquals(0, retiring.get().size(), "No more hosts to rename");
    }

    private void replace(Node node, ProvisioningTester tester) {
        if (!node.status().wantToRetire()) throw new IllegalArgumentException(node + " is not requested to retire");
        tester.nodeRepository().nodes().park(node.hostname(), true, Agent.system, getClass().getSimpleName());
        tester.nodeRepository().nodes().removeRecursively(node.hostname());
        provisionHosts(1, tester, "vespa-cloud.net");
    }

    private void provisionHosts(int count, ProvisioningTester tester, String domain) {
        List<Node> nodes = tester.makeProvisionedNodes(count, (index) -> "host-" + index + "." + domain, new Flavor(new NodeResources(2, 4, 8, 10)),
                                                       Optional.empty(), NodeType.host, 5, false);
        nodes = tester.nodeRepository().nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        tester.move(Node.State.ready, nodes);
        tester.activateTenantHosts();
    }

}
