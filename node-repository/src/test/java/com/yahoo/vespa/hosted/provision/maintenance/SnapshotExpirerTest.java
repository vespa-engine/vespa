package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.config.provision.SnapshotId;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author mpolden
 */
class SnapshotExpirerTest {

    @Test
    void expire() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyHosts(3, new NodeResources(8, 32, 1000, 10))
              .prepareAndActivateInfraApplication(NodeType.host);
        SnapshotExpirer maintainer = new SnapshotExpirer(tester.nodeRepository(), Duration.ofDays(1), new MockMetric());
        maintainer.maintain();

        // Deploy app
        ApplicationId app = ProvisioningTester.applicationId();
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("c1")).vespaVersion("8.0").build();
        int nodeCount = 3;
        NodeResources nodeResources = new NodeResources(4, 16, 100, 10);
        tester.deploy(app, clusterSpec, Capacity.from(new ClusterResources(nodeCount, 1, nodeResources)));

        // Create some snapshots
        NodeList nodes = tester.nodeRepository().nodes().list().nodeType(NodeType.tenant);
        Map<String, SnapshotId> snapshotsByHostname = new HashMap<>();
        for (var node : nodes) {
            Snapshot snapshot = tester.nodeRepository().snapshots().create(node.hostname(), tester.nodeRepository().clock().instant());
            tester.nodeRepository().snapshots().move(snapshot.id(), node.hostname(), Snapshot.State.created);
            tester.snapshotStore().add(snapshot.id());
            snapshotsByHostname.put(node.hostname(), snapshot.id());
        }

        // No snapshots are removed initially
        List<Snapshot> snapshots0 = tester.nodeRepository().snapshots().read();
        maintainer.maintain();
        assertEquals(snapshots0, tester.nodeRepository().snapshots().read());

        // One node is removed
        tester.deploy(app, clusterSpec, Capacity.from(new ClusterResources(nodeCount - 1, 1, nodeResources)));
        NodeList retired = tester.nodeRepository().nodes().list().owner(app).retired();
        tester.nodeRepository().nodes().setRemovable(retired, false);
        tester.deploy(app, clusterSpec, Capacity.from(new ClusterResources(nodeCount - 1, 1, nodeResources)));
        tester.nodeRepository().nodes().list(Node.State.inactive).owner(app)
              .forEach(node -> tester.nodeRepository().nodes().removeRecursively(node, true));

        // Orphaned snapshot is removed after being idle long enough
        maintainer.maintain();
        assertEquals(snapshots0, tester.nodeRepository().snapshots().read());
        tester.clock().advance(Duration.ofDays(1));
        maintainer.maintain();
        assertEquals(2, tester.nodeRepository().snapshots().read().size());
        assertFalse(tester.snapshotStore().list().contains(snapshotsByHostname.get(retired.first().get().hostname())));

        // A snapshot is restored
        Snapshot snapshot0 = tester.nodeRepository().snapshots().read().get(0);
        tester.nodeRepository().snapshots().restore(snapshot0.id(), snapshot0.hostname().value());
        tester.nodeRepository().snapshots().move(snapshot0.id(), snapshot0.hostname().value(), Snapshot.State.restored);

        // Restored snapshot is removed after being idle long enough
        List<Snapshot> snapshots1 = tester.nodeRepository().snapshots().read();
        maintainer.maintain();
        assertEquals(snapshots1, tester.nodeRepository().snapshots().read());
        tester.clock().advance(Duration.ofDays(1));
        maintainer.maintain();
        assertEquals(1, tester.nodeRepository().snapshots().read().size());
        assertFalse(tester.snapshotStore().list().contains(snapshot0.id()));
    }

}
