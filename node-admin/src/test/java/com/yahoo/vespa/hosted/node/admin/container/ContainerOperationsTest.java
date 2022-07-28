// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author mpolden
 */
public class ContainerOperationsTest {

    private final TestTaskContext context = new TestTaskContext();
    private final ContainerEngineMock containerEngine = new ContainerEngineMock();
    private final FileSystem fileSystem = TestFileSystem.create();
    private final ContainerOperations containerOperations = new ContainerOperations(containerEngine, new CGroupV2(fileSystem), fileSystem);

    @Test
    void no_managed_containers_running() {
        Container c1 = createContainer("c1", true);
        Container c2 = createContainer("c2", false);

        containerEngine.addContainer(c1);
        assertFalse(containerOperations.noManagedContainersRunning(context));

        containerEngine.removeContainer(context, c1);
        assertTrue(containerOperations.noManagedContainersRunning(context));

        containerEngine.addContainer(c2);
        assertTrue(containerOperations.noManagedContainersRunning(context));
    }

    @Test
    void retain_managed_containers() {
        Container c1 = createContainer("c1", true);
        Container c2 = createContainer("c2", true);
        Container c3 = createContainer("c3", false);
        containerEngine.addContainers(List.of(c1, c2, c3));

        assertEquals(3, containerEngine.listContainers(context).size());
        containerOperations.retainManagedContainers(context, Set.of(c1.name()));

        assertEquals(List.of(c1.name(), c3.name()), containerEngine.listContainers(context).stream()
                .map(PartialContainer::name)
                .sorted()
                .collect(Collectors.toList()));
    }

    private Container createContainer(String name, boolean managed) {
        return new Container(new ContainerId("id-of-" + name), new ContainerName(name), Instant.EPOCH, PartialContainer.State.running,
                             "image-id", DockerImage.EMPTY, Map.of(), 42, 43, name,
                             ContainerResources.UNLIMITED, List.of(), managed);
    }

}
