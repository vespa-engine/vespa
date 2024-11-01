package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.vespa.hosted.provision.backup.SnapshotId;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class SnapshotSerializerTest {

    @Test
    void serialization() {
        CloudAccount systemAccount = CloudAccount.from("aws:999123456789");
        Snapshot snapshot0 = new Snapshot(SnapshotId.of("ccf0b6de-3e06-4045-acba-458d99ef73e5"),
                                          HostName.of("host0.example.com"),
                                          Snapshot.State.creating,
                                          Snapshot.History.of(Snapshot.State.creating, Instant.ofEpochMilli(123)),
                                          new ClusterId(ApplicationId.from("t1", "a1", "i1"),
                                                        ClusterSpec.Id.from("c1")),
                                          0,
                                          CloudAccount.from("aws:000123456789")
        );
        Snapshot snapshot1 = new Snapshot(SnapshotId.of("7e45b44a-0f1a-4729-a4f4-20fff5d1e85d"),
                                          HostName.of("host1.example.com"),
                                          Snapshot.State.restored,
                                          Snapshot.History.of(Snapshot.State.restoring, Instant.ofEpochMilli(123))
                                                          .with(Snapshot.State.restored, Instant.ofEpochMilli(456)),
                                          new ClusterId(ApplicationId.from("t2", "a2", "i2"),
                                                        ClusterSpec.Id.from("c2")),
                                          2,
                                          CloudAccount.from("aws:777123456789")
        );
        assertEquals(snapshot0, SnapshotSerializer.fromSlime(SnapshotSerializer.toSlime(snapshot0), systemAccount));
        List<Snapshot> snapshots = List.of(snapshot0, snapshot1);
        assertEquals(snapshots, SnapshotSerializer.listFromSlime(SnapshotSerializer.toSlime(snapshots), systemAccount));
    }

}
