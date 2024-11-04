// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.config.provision.SnapshotId;
import com.yahoo.vespa.hosted.provision.persistence.SnapshotSerializer;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * @author mpolden
 */
public class SnapshotResponse extends SlimeJsonResponse {

    public SnapshotResponse(NodeRepository nodeRepository) {
        this(nodeRepository, Optional.empty(), Optional.empty());
    }

    public SnapshotResponse(NodeRepository nodeRepository, String hostname) {
        this(nodeRepository, Optional.of(hostname), Optional.empty());
    }

    public SnapshotResponse(NodeRepository nodeRepository, SnapshotId snapshotId, String hostname) {
        this(nodeRepository, Optional.of(hostname), Optional.of(snapshotId));
    }

    private SnapshotResponse(NodeRepository nodeRepository, Optional<String> hostname, Optional<SnapshotId> id) {
        if (id.isPresent() && hostname.isEmpty()) {
            throw new IllegalArgumentException("Must specify hostname when snapshotId is given");
        }
        Cursor root = slime.setObject();
        if (id.isPresent()) {
            SnapshotSerializer.toSlime(nodeRepository.snapshots().require(id.get(), hostname.get()), root);
        } else {
            final List<Snapshot> snapshots;
            if (hostname.isPresent()) {
                snapshots = nodeRepository.snapshots().read(hostname.get());
            } else {
                snapshots = nodeRepository.snapshots().read();
            }
            Cursor snapshotsArray = root.setArray("snapshots");
            snapshots.stream()
                     .sorted(Comparator.comparing(Snapshot::hostname)
                                       .thenComparing(snapshot -> snapshot.history().event(Snapshot.State.creating)
                                                                          .map(Snapshot.History.Event::at)
                                                                          .orElse(Instant.EPOCH)))
                     .forEach(snapshot -> SnapshotSerializer.toSlime(snapshot, snapshotsArray.addObject()));
        }
    }

}
