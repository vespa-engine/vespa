// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.backup.SnapshotId;

/**
 * A service that stores the actual files of a {@link com.yahoo.vespa.hosted.provision.backup.Snapshot}.
 *
 * @author mpolden
 */
public interface SnapshotStore {

    /** Delete all files belonging to given snapshot */
    void delete(SnapshotId snapshot, HostName hostname, CloudAccount cloudAccount);

}
