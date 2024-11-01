// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.backup;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.provisioning.SnapshotStore;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mpolden
 */
public class SnapshotStoreMock implements SnapshotStore {

    private final Set<SnapshotId> snapshots = new HashSet<>();

    public SnapshotStoreMock add(SnapshotId snapshot) {
        snapshots.add(snapshot);
        return this;
    }

    public Set<SnapshotId> list() {
        return Collections.unmodifiableSet(snapshots);
    }

    @Override
    public void delete(SnapshotId snapshot, HostName hostname, CloudAccount cloudAccount) {
        snapshots.remove(snapshot);
    }

}
