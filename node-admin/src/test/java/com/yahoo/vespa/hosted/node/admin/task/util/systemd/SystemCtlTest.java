// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.systemd;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Command;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestCommandSupplier;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
public class SystemCtlTest {
    private final TaskContext taskContext = mock(TaskContext.class);
    private final TestCommandSupplier testCommandSupplier = new TestCommandSupplier(taskContext);
    private final Function<TaskContext, Command> commandSupplier = context -> testCommandSupplier.get();

    @Test
    public void enable() throws Exception {
        testCommandSupplier
                .expectCommand(
                "systemctl show docker",
                0,
                "a=b\n" +
                        "UnitFileState=disabled\n" +
                        "bar=zoo\n")
                .expectCommand("systemctl enable docker", 0, "");

        SystemCtl.SystemCtlEnable enableDockerService = new SystemCtl(commandSupplier).enable("docker");
        assertTrue(enableDockerService.converge(taskContext));
    }

    @Test
    public void enableIsNoop() throws Exception {
        testCommandSupplier
                .expectCommand(
                        "systemctl show docker",
                        0,
                        "a=b\n" +
                                "UnitFileState=enabled\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl enable docker", 0, "");

        SystemCtl.SystemCtlEnable enableDockerService = new SystemCtl(commandSupplier).enable("docker");
        assertFalse(enableDockerService.converge(taskContext));
    }


    @Test
    public void enableCommandFailre() throws Exception {
        testCommandSupplier.expectCommand("systemctl show docker", 1, "error");
        SystemCtl.SystemCtlEnable enableDockerService = new SystemCtl(commandSupplier).enable("docker");
        try {
            enableDockerService.converge(taskContext);
            fail();
        } catch (CommandException e) {
            // success
        }
    }


    @Test
    public void start() throws Exception {
        testCommandSupplier
                .expectCommand(
                        "systemctl show docker",
                        0,
                        "a=b\n" +
                                "ActiveState=failed\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl start docker", 0, "");

        SystemCtl.SystemCtlStart startDockerService = new SystemCtl(commandSupplier).start("docker");
        assertTrue(startDockerService.converge(taskContext));
    }

    @Test
    public void startIsNoop() throws Exception {
        testCommandSupplier
                .expectCommand(
                        "systemctl show docker",
                        0,
                        "a=b\n" +
                                "ActiveState=active\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl start docker", 0, "");

        SystemCtl.SystemCtlStart startDockerService = new SystemCtl(commandSupplier).start("docker");
        assertFalse(startDockerService.converge(taskContext));
    }


    @Test
    public void startCommandFailre() throws Exception {
        testCommandSupplier.expectCommand("systemctl show docker", 1, "error");
        SystemCtl.SystemCtlStart startDockerService = new SystemCtl(commandSupplier).start("docker");
        try {
            startDockerService.converge(taskContext);
            fail();
        } catch (CommandException e) {
            // success
        }
    }


    @Test
    public void disable() throws Exception {
        testCommandSupplier
                .expectCommand(
                        "systemctl show docker",
                        0,
                        "a=b\n" +
                                "UnitFileState=enabled\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl disable docker", 0, "");

        assertTrue(new SystemCtl(commandSupplier).disable("docker").converge(taskContext));
    }

    @Test
    public void stop() throws Exception {
        testCommandSupplier
                .expectCommand(
                        "systemctl show docker",
                        0,
                        "a=b\n" +
                                "ActiveState=active\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl stop docker", 0, "");

        assertTrue(new SystemCtl(commandSupplier).stop("docker").converge(taskContext));
    }

    @Test
    public void restart() throws Exception {
        testCommandSupplier.expectCommand("systemctl restart docker", 0, "");
        assertTrue(new SystemCtl(commandSupplier).restart("docker").converge(taskContext));
    }
}