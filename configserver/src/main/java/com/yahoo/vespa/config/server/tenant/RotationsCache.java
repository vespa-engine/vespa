// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.curator.Curator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Persists assignment of rotations to an application to ZooKeeper.
 * The entries are RotationAssignments, which keep track of the container
 * cluster that is the target, the endpoint name, and the rotation used to
 * give availability to that cluster.
 *
 * This is v2 of that storage in a new directory.  Previously we only stored
 * the name of the rotation, since all the other information could be
 * calculated runtime.
 *
 * @author ogronnesby
 */
public class RotationsCache {
    private final Path cachePath;
    private final Curator curator;

    public RotationsCache(Path tenantPath, Curator curator) {
        this.cachePath = tenantPath.append("rotationsCache-v2/");
        this.curator = curator;
    }

    public Map<ClusterId, RotationAssignment> getRotationAssignment(ApplicationId applicationId) {
        final var optionalData = curator.getData(applicationPath(applicationId));
        return optionalData
                .map(SlimeUtils::jsonToSlime)
                .map(RotationsCache::entryFromSlime)
                .orElse(Collections.emptyMap());
    }

    public void putRotationAssignment(ApplicationId applicationId, Map<ClusterId, RotationAssignment> assignments) {
        if (assignments.isEmpty()) return;
        try {
            curator.set(
                    applicationPath(applicationId),
                    SlimeUtils.toJsonBytes(entryToSlime(assignments))
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing rotations of: " + applicationId, e);
        }
    }

    static Map<ClusterId, RotationAssignment> entryFromSlime(Slime slime) {
        final var assignmentMap = new HashMap<ClusterId, RotationAssignment>();

        slime.get().traverse((ObjectTraverser) (name, inspector) -> {
            final var containerId = new ClusterId(name);
            final var assignment = RotationAssignment.fromSlime(inspector);
            assignmentMap.put(containerId, assignment);
        });

        return Map.copyOf(assignmentMap);
    }

    static Slime entryToSlime(Map<ClusterId, RotationAssignment> assignments) {
        final var slime = new Slime();
        final var cursor = slime.setObject();

        assignments.forEach((clusterId, assignment) -> {
            final var assignmentCursor = cursor.setObject(clusterId.toString());
            assignment.toSlime(assignmentCursor);
        });

        return slime;
    }

    private Path applicationPath(ApplicationId applicationId) {
        return cachePath.append(applicationId.serializedForm());
    }
}
