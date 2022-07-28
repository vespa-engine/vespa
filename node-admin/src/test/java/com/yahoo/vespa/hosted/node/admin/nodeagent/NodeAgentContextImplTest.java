// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author freva
 */
public class NodeAgentContextImplTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final NodeAgentContext context = NodeAgentContextImpl.builder("container-1.domain.tld")
            .fileSystem(fileSystem).build();

    @Test
    void path_on_host_from_path_in_node_test() {
        assertEquals(
                "/data/vespa/storage/container-1",
                context.paths().of("/").pathOnHost().toString());

        assertEquals(
                "/data/vespa/storage/container-1/dev/null",
                context.paths().of("/dev/null").pathOnHost().toString());
    }

    @Test
    void path_in_container_must_be_absolute() {
        assertThrows(IllegalArgumentException.class, () -> {
            context.paths().of("some/relative/path");
        });
    }

    @Test
    void path_in_node_from_path_on_host_test() {
        assertEquals(
                "/dev/null",
                context.paths().fromPathOnHost(fileSystem.getPath("/data/vespa/storage/container-1/dev/null")).pathInContainer());
    }

    @Test
    void path_on_host_must_be_absolute() {
        assertThrows(IllegalArgumentException.class, () -> {
            context.paths().fromPathOnHost(Path.of("some/relative/path"));
        });
    }

    @Test
    void path_on_host_must_be_inside_container_storage_of_context() {
        assertThrows(IllegalArgumentException.class, () -> {
            context.paths().fromPathOnHost(fileSystem.getPath("/data/vespa/storage/container-2/dev/null"));
        });
    }

    @Test
    void path_on_host_must_be_inside_container_storage() {
        assertThrows(IllegalArgumentException.class, () -> {
            context.paths().fromPathOnHost(fileSystem.getPath("/home"));
        });
    }

    @Test
    void path_under_vespa_host_in_container_test() {
        assertEquals(
                "/opt/vespa",
                context.paths().underVespaHome("").pathInContainer());

        assertEquals(
                "/opt/vespa/logs/vespa/vespa.log",
                context.paths().underVespaHome("logs/vespa/vespa.log").pathInContainer());
    }

    @Test
    void path_under_vespa_home_must_be_relative() {
        assertThrows(IllegalArgumentException.class, () -> {
            context.paths().underVespaHome("/home");
        });
    }

    @Test
    void disabledTasksTest() {
        NodeAgentContext context1 = createContextWithDisabledTasks();
        assertFalse(context1.isDisabled(NodeAgentTask.DiskCleanup));
        assertFalse(context1.isDisabled(NodeAgentTask.CoreDumps));

        NodeAgentContext context2 = createContextWithDisabledTasks("root>UpgradeTask", "DiskCleanup", "node>CoreDumps");
        assertFalse(context2.isDisabled(NodeAgentTask.DiskCleanup));
        assertTrue(context2.isDisabled(NodeAgentTask.CoreDumps));
    }

    private NodeAgentContext createContextWithDisabledTasks(String... tasks) {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        flagSource.withListFlag(PermanentFlags.DISABLED_HOST_ADMIN_TASKS.id(), List.of(tasks), String.class);
        return NodeAgentContextImpl.builder("node123").fileSystem(fileSystem).flagSource(flagSource).build();
    }
}
