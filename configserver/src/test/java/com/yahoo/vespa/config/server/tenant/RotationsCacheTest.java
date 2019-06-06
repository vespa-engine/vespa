// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import java.util.HashMap;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class RotationsCacheTest {
    @Test
    public void assignmentDeserialization() {
        final var slime = new Slime();
        final var cursor = slime.setObject();

        cursor.setString("endpointId", "nallefisk");
        cursor.setString("containerId", "froskelosk");
        cursor.setString("rotationId", "rotterott");

        final var assignment = RotationAssignment.fromSlime(cursor);
        assertEquals("nallefisk", assignment.getEndpointId());
        assertEquals("froskelosk", assignment.getContainerId().toString());
        assertEquals("rotterott", assignment.getRotation().getId());
    }

    @Test
    public void assignmentSerialization() {
        final var assignment = new RotationAssignment(
                "sluttpeker",
                new Rotation("rotasjon"),
                new ClusterId("klynge")
        );

        final var serialized = new Slime();
        assignment.toSlime(serialized.setObject());

        assertEquals(assignment, RotationAssignment.fromSlime(serialized.get()));
    }

    @Test
    public void mapDeserialization() {
        final var slime = new Slime();
        final var cursor = slime.setObject();

        final var clusterId = new ClusterId("nalle");

        final var entry = cursor.setObject("nalle");
        entry.setString("endpointId", "nallefisk");
        entry.setString("containerId", clusterId.toString());
        entry.setString("rotationId", "rotterott");

        final var assignments = RotationsCache.entryFromSlime(slime);
        assertEquals(Set.of(clusterId), assignments.keySet());

        // check the entry
        final var assignment = assignments.get(clusterId);
        assertEquals(clusterId, assignment.getContainerId());
        assertEquals("nallefisk", assignment.getEndpointId());
        assertEquals(new Rotation("rotterott"), assignment.getRotation());
    }

    @Test
    public void mapSerialization() {
        final var assignments = new HashMap<ClusterId, RotationAssignment>();

        assignments.put(
                new ClusterId("the-nallefisk-1"),
                new RotationAssignment(
                        "the-endpoint-1",
                        new Rotation("the-rotation-1"),
                        new ClusterId("the-cluster-1")
                )
        );

        assignments.put(
                new ClusterId("the-nallefisk-2"),
                new RotationAssignment(
                        "the-endpoint-2",
                        new Rotation("the-rotation-2"),
                        new ClusterId("the-cluster-2")
                )
        );

        final var serialized = RotationsCache.entryToSlime(assignments);
        final var deserialized = RotationsCache.entryFromSlime(serialized);

        assertEquals(assignments, deserialized);
    }

    @Test
    public void cacheStoreAndLoad() {
        final var rotations = new RotationsCache(Path.createRoot().append("foo"), new MockCurator());
        final var assignments = new HashMap<ClusterId, RotationAssignment>();

        assignments.put(
                new ClusterId("the-nallefisk-1"),
                new RotationAssignment(
                        "the-endpoint-1",
                        new Rotation("the-rotation-1"),
                        new ClusterId("the-cluster-1")
                )
        );

        rotations.write(ApplicationId.defaultId(), assignments);
        final var fetched = rotations.read(ApplicationId.defaultId());

        assertEquals(assignments, fetched);
    }

}
