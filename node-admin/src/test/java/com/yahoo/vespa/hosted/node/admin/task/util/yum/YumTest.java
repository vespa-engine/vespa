// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.yum;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestCommandSupplier;
import org.junit.Test;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class YumTest {
    @Test
    public void testAlreadyInstalled() {
        TaskContext taskContext = mock(TaskContext.class);
        TestCommandSupplier commandSupplier = new TestCommandSupplier(taskContext);

        commandSupplier.expectCommand("yum list installed package-1", 0, "");
        commandSupplier.expectCommand("yum list installed package-2", 0, "");

        Yum yum = new Yum(taskContext, commandSupplier);
        yum.install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge();

        commandSupplier.verifyInvocations();
    }

    @Test
    public void testInstall() {
        TaskContext taskContext = mock(TaskContext.class);
        TestCommandSupplier commandSupplier = new TestCommandSupplier(taskContext);

        commandSupplier.expectCommand("yum list installed package-1", 0, "");
        commandSupplier.expectCommand("yum list installed package-2", 1, "");
        commandSupplier.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name package-1 package-2",
                0,
                "");

        Yum yum = new Yum(taskContext, commandSupplier);
        yum.install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge();

        commandSupplier.verifyInvocations();
    }

    @Test(expected = CommandException.class)
    public void testFailedInstall() {
        TaskContext taskContext = mock(TaskContext.class);
        TestCommandSupplier commandSupplier = new TestCommandSupplier(taskContext);

        commandSupplier.expectCommand("yum list installed package-1", 0, "");
        commandSupplier.expectCommand("yum list installed package-2", 1, "");
        commandSupplier.expectCommand(
                "yum install --assumeyes --enablerepo=repo-name package-1 package-2",
                1,
                "error");

        Yum yum = new Yum(taskContext, commandSupplier);
        yum.install("package-1", "package-2")
                .enableRepo("repo-name")
                .converge();
        fail();
    }
}