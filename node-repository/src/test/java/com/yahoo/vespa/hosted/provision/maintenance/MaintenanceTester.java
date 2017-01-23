package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic maintenance tester
 * 
 * @author bratseth
 */
public class MaintenanceTester {

    private final Curator curator = new MockCurator();
    public final ManualClock clock = new ManualClock(Instant.ofEpochMilli(0L)); // determinism
    private final Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
    private final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
    public final NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
                                                                     new MockNameResolver().mockAnyLookup());

    public void createReadyTenantNodes(int count) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("node" + i, "host" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes = nodeRepository.addNodes(nodes);
        nodeRepository.setReady(nodes);
    }

    public void createReadyHostNodes(int count) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("hostNode" + i, "realHost" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes);
        nodeRepository.setReady(nodes);
    }

}
