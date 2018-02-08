// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.systemd;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.ChildProcessFailureException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
public class SystemCtlTest {
    private final TaskContext taskContext = mock(TaskContext.class);
    private final TestTerminal terminal = new TestTerminal();

    @Test
    public void enable() throws Exception {
        terminal.expectCommand(
                "systemctl show docker 2>&1",
                0,
                "a=b\n" +
                        "UnitFileState=disabled\n" +
                        "bar=zoo\n")
                .expectCommand("systemctl enable docker 2>&1", 0, "");

        SystemCtl.SystemCtlEnable enableDockerService = new SystemCtl(terminal).enable("docker");
        assertTrue(enableDockerService.converge(taskContext));
    }

    @Test
    public void enableIsNoop() throws Exception {
        terminal.expectCommand(
                        "systemctl show docker 2>&1",
                        0,
                        "a=b\n" +
                                "UnitFileState=enabled\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl enable docker 2>&1", 0, "");

        SystemCtl.SystemCtlEnable enableDockerService = new SystemCtl(terminal).enable("docker");
        assertFalse(enableDockerService.converge(taskContext));
    }


    @Test
    public void enableCommandFailre() throws Exception {
        terminal.expectCommand("systemctl show docker 2>&1", 1, "error");
        SystemCtl.SystemCtlEnable enableDockerService = new SystemCtl(terminal).enable("docker");
        try {
            enableDockerService.converge(taskContext);
            fail();
        } catch (ChildProcessFailureException e) {
            // success
        }
    }


    @Test
    public void start() throws Exception {
        terminal.expectCommand(
                        "systemctl show docker 2>&1",
                        0,
                        "a=b\n" +
                                "ActiveState=failed\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl start docker 2>&1", 0, "");

        SystemCtl.SystemCtlStart startDockerService = new SystemCtl(terminal).start("docker");
        assertTrue(startDockerService.converge(taskContext));
    }

    @Test
    public void startIsNoop() throws Exception {
        terminal.expectCommand(
                        "systemctl show docker 2>&1",
                        0,
                        "a=b\n" +
                                "ActiveState=active\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl start docker 2>&1", 0, "");

        SystemCtl.SystemCtlStart startDockerService = new SystemCtl(terminal).start("docker");
        assertFalse(startDockerService.converge(taskContext));
    }


    @Test
    public void startCommandFailre() throws Exception {
        terminal.expectCommand("systemctl show docker 2>&1", 1, "error");
        SystemCtl.SystemCtlStart startDockerService = new SystemCtl(terminal).start("docker");
        try {
            startDockerService.converge(taskContext);
            fail();
        } catch (ChildProcessFailureException e) {
            // success
        }
    }


    @Test
    public void disable() throws Exception {
        terminal.expectCommand(
                        "systemctl show docker 2>&1",
                        0,
                        "a=b\n" +
                                "UnitFileState=enabled\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl disable docker 2>&1", 0, "");

        assertTrue(new SystemCtl(terminal).disable("docker").converge(taskContext));
    }

    @Test
    public void stop() throws Exception {
        terminal.expectCommand(
                        "systemctl show docker 2>&1",
                        0,
                        "a=b\n" +
                                "ActiveState=active\n" +
                                "bar=zoo\n")
                .expectCommand("systemctl stop docker 2>&1", 0, "");

        assertTrue(new SystemCtl(terminal).stop("docker").converge(taskContext));
    }

    @Test
    public void restart() throws Exception {
        terminal.expectCommand("systemctl restart docker 2>&1", 0, "");
        assertTrue(new SystemCtl(terminal).restart("docker").converge(taskContext));
    }
}