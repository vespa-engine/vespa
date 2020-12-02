// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class NodeAgentContextImplTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final NodeAgentContext context = new NodeAgentContextImpl.Builder("container-1.domain.tld")
            .fileSystem(fileSystem).build();

    @Test
    public void path_on_host_from_path_in_node_test() {
        assertEquals(
                "/home/docker/container-storage/container-1",
                context.pathOnHostFromPathInNode("/").toString());

        assertEquals(
                "/home/docker/container-storage/container-1/dev/null",
                context.pathOnHostFromPathInNode("/dev/null").toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void path_in_container_must_be_absolute() {
        context.pathOnHostFromPathInNode("some/relative/path");
    }

    @Test
    public void path_in_node_from_path_on_host_test() {
        assertEquals(
                "/dev/null",
                context.pathInNodeFromPathOnHost(fileSystem.getPath("/home/docker/container-storage/container-1/dev/null")).toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void path_on_host_must_be_absolute() {
        context.pathInNodeFromPathOnHost("some/relative/path");
    }

    @Test(expected=IllegalArgumentException.class)
    public void path_on_host_must_be_inside_container_storage_of_context() {
        context.pathInNodeFromPathOnHost(fileSystem.getPath("/home/docker/container-storage/container-2/dev/null"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void path_on_host_must_be_inside_container_storage() {
        context.pathInNodeFromPathOnHost(fileSystem.getPath("/home"));
    }

    @Test
    public void path_under_vespa_host_in_container_test() {
        assertEquals(
                "/opt/vespa",
                context.pathInNodeUnderVespaHome("").toString());

        assertEquals(
                "/opt/vespa/logs/vespa/vespa.log",
                context.pathInNodeUnderVespaHome("logs/vespa/vespa.log").toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void path_under_vespa_home_must_be_relative() {
        context.pathInNodeUnderVespaHome("/home");
    }

    @Test
    public void disabledTasksTest() {
        NodeAgentContext context1 = createContextWithDisabledTasks();
        assertFalse(context1.isDisabled(NodeAgentTask.DiskCleanup));
        assertFalse(context1.isDisabled(NodeAgentTask.CoreDumps));

        NodeAgentContext context2 = createContextWithDisabledTasks("root>UpgradeTask", "DiskCleanup", "node>CoreDumps");
        assertFalse(context2.isDisabled(NodeAgentTask.DiskCleanup));
        assertTrue(context2.isDisabled(NodeAgentTask.CoreDumps));
    }

    private static NodeAgentContext createContextWithDisabledTasks(String... tasks) {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        flagSource.withListFlag(PermanentFlags.DISABLED_HOST_ADMIN_TASKS.id(), List.of(tasks), String.class);
        return new NodeAgentContextImpl.Builder("node123").flagSource(flagSource).build();
    }
}
