package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author mpolden
 */
class SnapshotSerializerTest {

    @Test
    void serialization() {
        Snapshot snapshot0 = new Snapshot("id0",
                                          HostName.of("host0.example.com"),
                                          Snapshot.State.creating,
                                          Instant.ofEpochMilli(123),
                                          new ClusterId(ApplicationId.from("t1", "a1", "i1"),
                                                        ClusterSpec.Id.from("c1")),
                                          1);
        Snapshot snapshot1 = new Snapshot("id1",
                                          HostName.of("host1.example.com"),
                                          Snapshot.State.restored,
                                          Instant.ofEpochMilli(456),
                                          new ClusterId(ApplicationId.from("t2", "a2", "i2"),
                                                        ClusterSpec.Id.from("c2")),
                                          2);
        assertEquals(snapshot0, SnapshotSerializer.fromSlime(SnapshotSerializer.toSlime(snapshot0)));
        List<Snapshot> snapshots = List.of(snapshot0, snapshot1);
        assertEquals(snapshots, SnapshotSerializer.listFromSlime(SnapshotSerializer.toSlime(snapshots)));
    }

}
