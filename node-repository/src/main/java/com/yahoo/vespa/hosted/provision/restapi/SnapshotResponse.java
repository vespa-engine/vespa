// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.vespa.hosted.provision.persistence.SnapshotSerializer;

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

    public SnapshotResponse(NodeRepository nodeRepository, String hostname, String snapshotId) {
        this(nodeRepository, Optional.of(hostname), Optional.of(snapshotId));
    }

    private SnapshotResponse(NodeRepository nodeRepository, Optional<String> hostname, Optional<String> snapshotId) {
        if (snapshotId.isPresent() && hostname.isEmpty()) {
            throw new IllegalArgumentException("Must specify hostname when snapshotId is given");
        }
        Cursor root = slime.setObject();
        if (snapshotId.isPresent()) {
            SnapshotSerializer.toSlime(nodeRepository.snapshots().require(snapshotId.get(), hostname.get()), root);
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
                                       .thenComparing(Snapshot::createdAt))
                     .forEach(snapshot -> SnapshotSerializer.toSlime(snapshot, snapshotsArray.addObject()));
        }
    }

}
