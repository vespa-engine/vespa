package com.yahoo.vespa.hosted.controller.api.resource;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ResourceSnapshotTest {
    @Test
    void test_adding_resources_collapse_dimensions() {
        var nodes = List.of(
                nodeWithResources(NodeResources.zero().with(NodeResources.DiskSpeed.fast)),
                nodeWithResources(NodeResources.zero().with(NodeResources.DiskSpeed.slow)));

        // This should be OK and not throw exception
        var snapshot = ResourceSnapshot.from(nodes, Instant.EPOCH, ZoneId.defaultId());

        assertEquals(NodeResources.DiskSpeed.any, snapshot.resources().diskSpeed());
    }

    @Test
    void test_adding_resources_fail() {
        var nodes = List.of(
                nodeWithResources(NodeResources.zero().with(NodeResources.Architecture.x86_64)),
                nodeWithResources(NodeResources.zero().with(NodeResources.Architecture.arm64)));

        try {
            ResourceSnapshot.from(nodes, Instant.EPOCH, ZoneId.defaultId());
            fail("Should throw an exception");
        } catch (IllegalArgumentException e) {
            // this should happen
        }
    }

    private Node nodeWithResources(NodeResources resources) {
        return Node.builder()
                .hostname("a")
                .owner(ApplicationId.defaultId())
                .resources(resources)
                .build();
    }
}
